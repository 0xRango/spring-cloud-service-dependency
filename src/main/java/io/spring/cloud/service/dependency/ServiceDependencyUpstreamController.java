package io.spring.cloud.service.dependency;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ServiceDependencyUpstreamController {

	@Autowired
	private ServiceDependencyConfigBean serviceDependencySettings;

	@Value("${spring.application.name}")
	private String serviceId;

	@Autowired
	private LoadBalancerClient client;

	@Autowired
	private RestTemplate restTemplate;

	@RequestMapping("/upstream")
	public List<String> upstreamServices() {
		return serviceDependencySettings.getRequires();
	}

	@RequestMapping("/dependency-tree")
	public String dependencyTree() {
		ServiceNode node = ServiceDependencyConfigBean.travelServiceGraph(serviceId,
				serviceDependencySettings.getRequires(), client, restTemplate);
		return node.toDependencyTree();
	}

}
