package io.spring.cloud.service.dependency;

import java.util.ArrayList;
import java.util.List;

public class ServiceNode {

	private String name;
	private boolean avaiable;
	private boolean dependencyCycleDetected;
	private List<ServiceNode> children = new ArrayList<ServiceNode>();
	private ServiceNode parent;

	public ServiceNode() {
	}

	public ServiceNode(String serviceId) {
		this.name = serviceId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isAvaiable() {
		return avaiable;
	}

	public void setAvaiable(boolean avaiable) {
		this.avaiable = avaiable;
	}

	public boolean isDependencyCycleDetected() {
		return dependencyCycleDetected;
	}

	public void setDependencyCycleDetected(boolean dependencyCycleDetected) {
		this.dependencyCycleDetected = dependencyCycleDetected;
	}

	public List<ServiceNode> getChildren() {
		return children;
	}

	public void setChildren(List<ServiceNode> children) {
		this.children = children;
	}

	public ServiceNode getParent() {
		return parent;
	}

	public void setParent(ServiceNode parent) {
		this.parent = parent;
	}

	public String toDependencyTree() {
		return toDependencyTree(0, false);
	}

	private String toDependencyTree(int level, boolean isLast) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < level; i++)
			sb.append("  ");
		if (isLast)
			sb.append("\\- ").append(name);
		else
			sb.append("+- ").append(name);
		if (isDependencyCycleDetected())
			sb.append(" **ERROR** Dependency Cycle detected");
		else if (!isAvaiable())
			sb.append(" **ERROR** Service is unavailable");
		sb.append("\n");
		int i = 0;
		for (ServiceNode child : getChildren()) {
			sb.append(child.toDependencyTree(level + 1, ++i == getChildren().size()));
		}
		return sb.toString();
	}
}
