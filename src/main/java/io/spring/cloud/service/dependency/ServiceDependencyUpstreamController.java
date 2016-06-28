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
		int i = 0;
		for (String upstreamId : serviceDependencySettings.getRequires()) {
			if (++i == serviceDependencySettings.getRequires().size())
				sb.append("   \\- ").append(upstreamId);
			else
				sb.append("   +- ").append(upstreamId);
			List<String> services = new ArrayList<>();
			services.add(serviceId);
			checkUpstream(services, upstreamId, 1, sb);
		}
		return sb.toString();
	}

	private void checkUpstream(List<String> services, String upstreamId, int level, StringBuffer sb) {
		if (services.contains(upstreamId)) {
			sb.append(" **ERROR** denpendency cycle was detected\n");
			return;
		}
		ServiceInstance instance = client.choose(upstreamId);
		if (instance != null) {
			sb.append("\n");
			ParameterizedTypeReference<List<String>> ptr = new ParameterizedTypeReference<List<String>>() {
			};
			List<String> upstreamDependency = restTemplate
					.exchange(String.format("http://%s:%s/upstream", instance.getHost(), instance.getPort()),
							HttpMethod.GET, null, ptr)
					.getBody();
			if (upstreamDependency != null) {
				int i = 0;
				for (String dependency : upstreamDependency) {
					i++;
					for (int k = 0; k <= level; k++) {
						sb.append("   ");
					}
					if (i == upstreamDependency.size())
						sb.append("\\- ").append(dependency);
					else
						sb.append("+- ").append(dependency);
					services.add(dependency);
					checkUpstream(services, dependency, level + 1, sb);
				}
			}
		} else {
			sb.append(" **ERROR** service instance not found, please retry later");
		}
	}
}
