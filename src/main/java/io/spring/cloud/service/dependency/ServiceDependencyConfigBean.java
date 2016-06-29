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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.ZonePreferenceServerListFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.client.RestTemplate;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListFilter;

@ConfigurationProperties(prefix = "service-dependency")
public class ServiceDependencyConfigBean implements SchedulingConfigurer {

	private static Logger logger = LoggerFactory.getLogger(ServiceDependencyConfigBean.class);

	@Value("${spring.application.name}")
	private String serviceId;

	@Autowired
	private LoadBalancerClient client;

	@Autowired
	private RestTemplate restTemplate;

	private boolean terminateOnDependencyCycleFound;

	private boolean terminateOnRequiresNotFound;

	private List<String> requires = new ArrayList<String>();

	private boolean requiresChecked;

	private boolean dependencyCycleChecked;

	@Bean
	@ConditionalOnMissingBean
	@LoadBalanced
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Autowired
	private ApplicationContext applicationContext;

	public void setRequires(List<String> requires) {
		this.requires = requires;
	}

	public List<String> getRequires() {
		return requires;
	}

	public boolean isTerminateOnDependencyCycleFound() {
		return terminateOnDependencyCycleFound;
	}

	public void setTerminateOnDependencyCycleFound(boolean terminateOnDependencyCycleFound) {
		this.terminateOnDependencyCycleFound = terminateOnDependencyCycleFound;
	}

	public boolean isTerminateOnRequiresNotFound() {
		return terminateOnRequiresNotFound;
	}

	public void setTerminateOnRequiresNotFound(boolean terminateOnRequiresNotFound) {
		this.terminateOnRequiresNotFound = terminateOnRequiresNotFound;
	}

	@Bean
	public ServerListFilter<Server> ribbonServerListFilter(IClientConfig config) {
		ZonePreferenceServerListFilter filter = new ZonePreferenceServerListFilter() {
			@Override
			public List<Server> getFilteredListOfServers(List<Server> servers) {
				return super.getFilteredListOfServers(servers).stream().filter(server -> {
					String serviceId = server.getMetaInfo().getAppName();
					return getRequires().contains(serviceId.toLowerCase());
				}).collect(Collectors.toList());
			}
		};
		filter.initWithNiwsConfig(config);
		return filter;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.addTriggerTask(this::checkRequireServices, context -> requiresChecked ? null : defer(10));
		if (isTerminateOnDependencyCycleFound()) {
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
		for (String serviceId : getRequires()) {
			ServiceInstance instance = client.choose(serviceId);
			if (instance == null) {
				unavailableServices.add(serviceId);
			}
		}
		if (unavailableServices.size() > 0) {
			logger.warn("The following services are unavailable: {}", StringUtils.join(unavailableServices.toArray(), ","));
			if (isTerminateOnRequiresNotFound()) {
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

		ServiceNode node = travelServiceGraph(serviceId, getRequires(), client, restTemplate);

		logger.info("Service Dependency Tree:\n{}", node.toDependencyTree());

		if (node.isHasDependencyCycle()) {
			logger.error("Service Dependency Cycle was detected, will terminate application");
			SpringApplication.exit(applicationContext);
		} else if (node.isHasUnavaiable()) {
			logger.error("There are unavailable service, will try dependency check later");
		} else {
			dependencyCycleChecked = true;
		}
	}

	public static ServiceNode travelServiceGraph(String serviceName, List<String> requires, LoadBalancerClient client,
			RestTemplate restTemplate) {
		ServiceNode root = new ServiceNode(serviceName);
		root.setAvaiable(true);
		for (String upstreamId : requires) {
			ServiceNode node = new ServiceNode(upstreamId);
			node.setParent(root);
			root.getChildren().add(node);
			checkUpstream(root, node, client, restTemplate);
		}
		return root;
	}

	private static void checkUpstream(ServiceNode root, ServiceNode node, LoadBalancerClient client,
			RestTemplate restTemplate) {
		if (checkDependencyCycle(node, new ArrayList<String>())) {
			node.setDependencyCycleDetected(true);
			root.setHasDependencyCycle(true);
			return;
		}
		ServiceInstance instance = client.choose(node.getName());
		if (instance != null) {
			node.setAvaiable(true);
			ParameterizedTypeReference<List<String>> ptr = new ParameterizedTypeReference<List<String>>() {
			};
			ResponseEntity<List<String>> result = restTemplate
					.exchange(String.format("http://%s/upstream", node.getName()), HttpMethod.GET, null, ptr);
			if (result.getStatusCode() == HttpStatus.OK) {
				List<String> upstreamDependency = result.getBody();
				if (upstreamDependency != null) {
					for (String dependency : upstreamDependency) {
						ServiceNode child = new ServiceNode(dependency);
						child.setParent(node);
						node.getChildren().add(child);
						checkUpstream(root, child, client, restTemplate);
					}
				}
			}
		} else {
			root.setHasUnavaiable(true);
		}
	}

	private static boolean checkDependencyCycle(ServiceNode node, List<String> services) {
		if (services.contains(node.getName())) {
			return true;
		}
		services.add(node.getName());
		return node.getParent() == null ? false : checkDependencyCycle(node.getParent(), services);
	}
}
