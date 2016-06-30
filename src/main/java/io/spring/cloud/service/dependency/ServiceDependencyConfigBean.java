package io.spring.cloud.service.dependency;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "service-dependency")
public class ServiceDependencyConfigBean {


	private boolean terminateOnDependencyCycleFound;

	private boolean terminateOnRequiresNotFound;

	private List<String> requires = new ArrayList<String>();

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

	public List<String> getRequires() {
		return requires;
	}

	public void setRequires(List<String> requires) {
		this.requires = requires;
	}

}
