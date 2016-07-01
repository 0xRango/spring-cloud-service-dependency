package io.spring.cloud.service.dependency;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("service-dependency")
public class ServiceDependencyUpstreamController {
	@Autowired
	private ServiceDependencyConfigBean config;

	@Value("${spring.application.name}")
	private String serviceId;

	@Autowired
	private LoadBalancerClient client;

	@Autowired
	private RestTemplate restTemplate;

	public ServiceDependencyUpstreamController() {
		System.out.println("");
	}

	@Bean
	@ConditionalOnMissingBean
	@LoadBalanced
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@RequestMapping(path = "/upstream", method = RequestMethod.POST)
	public ServiceNode upstream(@RequestBody List<String> services) {
		ServiceNode node = new ServiceNode(serviceId);
		node.setAvaiable(true);
		if (services.contains(serviceId)) {
			node.setDependencyCycleDetected(true);
		} else {
			services.add(serviceId);
			for (String upstreamId : config.getRequires()) {
				ServiceNode upstream = null;
				if (services.contains(upstreamId)) {
					upstream = new ServiceNode(upstreamId);
					upstream.setDependencyCycleDetected(true);
				} else {
					try {
						ResponseEntity<ServiceNode> result = restTemplate.exchange(
								String.format("http://%s/service-dependency/upstream", upstreamId), HttpMethod.POST,
								new HttpEntity<>(services), ServiceNode.class);
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
				}
				node.getChildren().add(upstream);
			}
		}
		return node;
	}

	@RequestMapping("/dependency-tree")
	public String dependencyTree() {
		ServiceNode node = ServiceDependencyConfiguration.travelServiceGraph(serviceId, config.getRequires(), client,
				restTemplate);
		return node.toDependencyTree();
	}

}
