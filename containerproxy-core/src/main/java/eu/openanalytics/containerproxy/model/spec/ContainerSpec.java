package eu.openanalytics.containerproxy.model.spec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ContainerSpec {

	private String image;
	private String[] cmd;
	private Map<String, String> env;
	private String envFile;
	private String network;
	private String[] networkConnections;
	private String[] dns;
	private String[] volumes;
	private Map<String, Integer> portMapping = new HashMap<>();
	private boolean privileged;
	private String memoryRequest;
	private String memoryLimit;
	private String cpuRequest;
	private String cpuLimit;
	private Map<String, String> labels = new HashMap<>();
	private Map<String, String> settings = new HashMap<>();

	/**
	 * RuntimeLabels are labels which are calculated at runtime and contain metadata about the proxy.
	 * These should not be included in API responses.
	 *
	 * The boolean in the pair indicates whether the value is "safe". Safe values are calculated by
	 * ShinyProxy itself and contain no user data.
	 * In practice, safe labels are saved as Kubernetes labels and non-safe labels are saved as
	 * Kubernetes annotations.
	 */
	private Map<String, Pair<Boolean, String>> runtimeLabels = new HashMap<>();

	public String getImage() {
		return image;
	}
	public void setImage(String image) {
		this.image = image;
	}
	public String[] getCmd() {
		return cmd;
	}
	public void setCmd(String[] cmd) {
		this.cmd = cmd;
	}
	public Map<String, String> getEnv() {
		return env;
	}
	public void setEnv(Map<String, String> env) {
		this.env = env;
	}
	public String getEnvFile() {
		return envFile;
	}
	public void setEnvFile(String envFile) {
		this.envFile = envFile;
	}
	public String getNetwork() {
		return network;
	}
	public void setNetwork(String network) {
		this.network = network;
	}
	public String[] getNetworkConnections() {
		return networkConnections;
	}
	public void setNetworkConnections(String[] networkConnections) {
		this.networkConnections = networkConnections;
	}
	public String[] getDns() {
		return dns;
	}
	public void setDns(String[] dns) {
		this.dns = dns;
	}
	public String[] getVolumes() {
		return volumes;
	}
	public void setVolumes(String[] volumes) {
		this.volumes = volumes;
	}
	public Map<String, Integer> getPortMapping() {
		return portMapping;
	}
	public void setPortMapping(Map<String, Integer> portMapping) {
		this.portMapping = portMapping;
	}
	public boolean isPrivileged() {
		return privileged;
	}
	public void setPrivileged(boolean privileged) {
		this.privileged = privileged;
	}
	public String getMemoryRequest() {
		return memoryRequest;
	}
	public void setMemoryRequest(String memoryRequest) {
		this.memoryRequest = memoryRequest;
	}
	public String getMemoryLimit() {
		return memoryLimit;
	}
	public void setMemoryLimit(String memoryLimit) {
		this.memoryLimit = memoryLimit;
	}
	public String getCpuRequest() {
		return cpuRequest;
	}
	public void setCpuRequest(String cpuRequest) {
		this.cpuRequest = cpuRequest;
	}
	public String getCpuLimit() {
		return cpuLimit;
	}
	public void setCpuLimit(String cpuLimit) {
		this.cpuLimit = cpuLimit;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

	@JsonIgnore
	public Map<String, Pair<Boolean, String>> getRuntimeLabels() {
		return runtimeLabels;
	}

	public void setRuntimeLabels(Map<String, Pair<Boolean, String>> runtimeLabels) {
		this.runtimeLabels = runtimeLabels;
	}

	public void addRuntimeLabel(String key, Boolean safe, String value) {
		if (this.runtimeLabels.containsKey(key)) {
			throw new IllegalStateException("Cannot add duplicate label with key " + key);
		} else {
			runtimeLabels.put(key, Pair.of(safe, value));
		}
	}
	
	public Map<String, String> getSettings() {
		return settings;
	}
	
	public void setSettings(Map<String, String> settings) {
		this.settings = settings;
	}
	
	public void copy(ContainerSpec target) {
		target.setImage(image);
		if (cmd != null) target.setCmd(Arrays.copyOf(cmd, cmd.length));
		if (env != null) {
			if (target.getEnv() == null) target.setEnv(new HashMap<>());
			target.getEnv().putAll(env);
		}
		target.setEnvFile(envFile);
		target.setNetwork(network);
		if (networkConnections != null) target.setNetworkConnections(Arrays.copyOf(networkConnections, networkConnections.length));
		if (dns != null) target.setDns(Arrays.copyOf(dns, dns.length));
		if (volumes != null) target.setVolumes(Arrays.copyOf(volumes, volumes.length));
		if (portMapping != null) {
			if (target.getPortMapping() == null) target.setPortMapping(new HashMap<>());
			target.getPortMapping().putAll(portMapping);
		}
		target.setMemoryRequest(memoryRequest);
		target.setMemoryLimit(memoryLimit);
		target.setCpuRequest(cpuRequest);
		target.setCpuLimit(cpuLimit);
		target.setPrivileged(privileged);
		if (labels != null) {
			if (target.getLabels() == null) target.setLabels(new HashMap<>());
			target.getLabels().putAll(labels);
		}
		if (settings != null) {
			if (target.getSettings() == null) target.setSettings(new HashMap<>());
			target.getSettings().putAll(settings);
		}
	}
}