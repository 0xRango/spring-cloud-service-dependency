package io.spring.cloud.service.dependency;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
		StringBuffer sb = new StringBuffer();
		sb.append("+- ").append(serviceId).append("\n");
		for (String upstreamId : serviceDependencySettings.getRequires()) {
			sb.append("|  +- ").append(upstreamId).append("\n");
			List<String> services = new ArrayList<>();
			services.add(serviceId);
			checkUpstream(services, upstreamId, 1, sb);
		}
		return sb.toString();
	}

	private void checkUpstream(List<String> services, String upstreamId, int level, StringBuffer sb) {
		if (services.contains(upstreamId)) {
			for (int i = 0; i <= level; i++) {
				sb.append("|  ");
			}
			sb.append("+- ERROR denpendency cycle was detected\n");
			return;
		}
		ServiceInstance instance = client.choose(upstreamId);
		if (instance != null) {
			ParameterizedTypeReference<List<String>> ptr = new ParameterizedTypeReference<List<String>>() {
			};
			List<String> upstreamDependency = restTemplate
					.exchange(String.format("http://%s:%s/upstream", instance.getHost(), instance.getPort()),
							HttpMethod.GET, null, ptr)
					.getBody();
			if (upstreamDependency != null) {
				for (String dependency : upstreamDependency) {
					for (int i = 0; i <= level; i++) {
						sb.append("|  ");
					}
					sb.append("+- ").append(dependency).append("\n");
					services.add(dependency);
					checkUpstream(services, dependency, level + 1, sb);
				}
			}
		} else {
			for (int i = 0; i <= level; i++) {
				sb.append("|  ");
			}
			sb.append("ERROR service instance not found, please retry later");
		}
	}
}
