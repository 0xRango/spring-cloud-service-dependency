package io.spring.cloud.service.dependency;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.ribbon.ZonePreferenceServerListFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.client.RestTemplate;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListFilter;

public class ServiceDependencyConfiguration implements SchedulingConfigurer {

	private static Logger logger = LoggerFactory.getLogger(ServiceDependencyConfiguration.class);

	@Value("${spring.application.name}")
	private String serviceId;

	@Value("${ribbon.client.name:client}")
	private String clientName = "client";

	@Autowired
	private LoadBalancerClient client;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ServiceDependencyConfigBean config;

	private boolean requiresChecked;

	private boolean dependencyCycleChecked;

	@Autowired
	private ApplicationContext applicationContext;

	@Bean
	public ServerListFilter<Server> ribbonServerListFilter(SpringClientFactory springClientFactory) {
		ZonePreferenceServerListFilter serverListFilter = new ZonePreferenceServerListFilter() {
			@Override
			public List<Server> getFilteredListOfServers(List<Server> servers) {
				return super.getFilteredListOfServers(servers).stream().filter(server -> {
					String serviceId = server.getMetaInfo().getAppName();
					return config.getRequires().contains(serviceId.toLowerCase());
				}).collect(Collectors.toList());
			}
		};
		return serverListFilter;
	}

	@Bean
	@ConditionalOnMissingBean
	@LoadBalanced
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.addTriggerTask(this::checkRequireServices, context -> requiresChecked ? null : defer(10));
		if (config.isTerminateOnDependencyCycleFound()) {
			taskRegistrar.addTriggerTask(this::checkDependencyCycle,
					context -> dependencyCycleChecked ? null : defer(10));
		}
	}

	private Date defer(int seconds) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, seconds);
		return cal.getTime();
	}

	private void checkRequireServices() {
		logger.info("Checking require services");
		List<String> unavailableServices = new ArrayList<String>();
		for (String serviceId : config.getRequires()) {
			ServiceInstance instance = null;
			try {
				instance = client.choose(serviceId);
			} catch (Exception e) {
				System.out.println(e);
			}
			if (instance == null) {
				unavailableServices.add(serviceId);
			}
		}
		if (unavailableServices.size() > 0) {
			logger.warn("The following services are unavailable: {}",
					StringUtils.join(unavailableServices.toArray(), ", "));
			if (config.isTerminateOnRequiresNotFound()) {
				logger.error("Terminate application");
				SpringApplication.exit(applicationContext);
			}
		} else {
			logger.info("All require services are up");
			requiresChecked = true;
		}
	}

	private void checkDependencyCycle() {
		logger.info("Checking service dependency cycle");
		dependencyCycleChecked = false;

		ServiceNode node = travelServiceGraph(serviceId, config.getRequires(), client, restTemplate);

		logger.info("Service Dependency Tree:\n{}", node.toDependencyTree());

		boolean hasDependencyCycle = checkDependencyCycle(node);
		if (hasDependencyCycle) {
			logger.error("Service Dependency Cycle was detected, will terminate application");
			SpringApplication.exit(applicationContext);
			return;
		} else {
			boolean hasUnavailable = checkHasUnavailable(node);
			if (hasUnavailable) {
				logger.error("There are unavailable service(s), will try dependency check later");
				return;
			}
		}
		logger.info("Dependency cycle not found");
		dependencyCycleChecked = true;

	}

	private boolean checkHasUnavailable(ServiceNode node) {
		if (!node.isAvaiable())
			return true;
		for (ServiceNode upstream : node.getChildren()) {
			if (checkHasUnavailable(upstream))
				return true;
		}
		return false;
	}

	private boolean checkDependencyCycle(ServiceNode node) {
		if (node.isDependencyCycleDetected())
			return true;
		for (ServiceNode upstream : node.getChildren()) {
			if (checkDependencyCycle(upstream))
				return true;
		}
		return false;
	}

	public static ServiceNode travelServiceGraph(String serviceName, List<String> requires, LoadBalancerClient client,
			RestTemplate restTemplate) {
		ServiceNode root = new ServiceNode(serviceName);
		root.setAvaiable(true);
		for (String upstreamId : requires) {
			List<String> services = new ArrayList<>();
			services.add(serviceName);
			ServiceNode upstream = null;
			try {
				ResponseEntity<ServiceNode> result = restTemplate.exchange(
						String.format("http://%s/upstream", upstreamId), HttpMethod.POST, new HttpEntity<>(services),
						ServiceNode.class);
				if (result.getStatusCode() == HttpStatus.OK) {
					upstream = result.getBody();
				} else {
					upstream = new ServiceNode(upstreamId);
					upstream.setAvaiable(true);
				}
			} catch (Exception e) {
				upstream = new ServiceNode(upstreamId);
				upstream.setAvaiable(false);
			}
			root.getChildren().add(upstream);
		}
		return root;
	}

}
