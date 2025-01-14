/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.shinyproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import eu.openanalytics.containerproxy.model.spec.SubApplicationSpec;
import eu.openanalytics.containerproxy.util.SessionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxyAccessControl;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

/**
 * This component converts proxy specs from the 'ShinyProxy notation' into the 'ContainerProxy' notation.
 * ShinyProxy notation is slightly more compact, and omits several things that Shiny apps do not need,
 * such as definition of multiple containers.
 *
 * Also, if no port is specified, a port mapping is automatically created for Shiny port 3838.
 */
@Component
@ConfigurationProperties(prefix = "shinyproxy")
public class ShinyProxySpecProvider implements IProxySpecProvider {
  
  private List<ProxySpec> specs = new ArrayList<>();
  
  private static Environment environment;
  
  @Autowired
  public void setEnvironment(Environment env){
    ShinyProxySpecProvider.environment = env;
  }
  
  @PostConstruct
  public void afterPropertiesSet() {
    this.specs.stream().collect(Collectors.groupingBy(ProxySpec::getId)).forEach((id, duplicateSpecs) -> {
      if (duplicateSpecs.size() > 1) throw new IllegalArgumentException(String.format("Configuration error: spec with id '%s' is defined multiple times", id));
    });
  }
  
  public List<ProxySpec> getSpecs() {
    return new ArrayList<>(specs);
  }
  
  public ProxySpec getSpec(String id) {
    if (id == null || id.isEmpty()) return null;
    return specs.stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
  }
  
  public void setSpecs(List<ShinyProxySpec> specs) {
    this.specs = specs.stream().map(ShinyProxySpecProvider::convert).collect(Collectors.toList());
  }
  
  public static String getPublicPath(String appName) {
    String contextPath = SessionHelper.getContextPath(environment, true);
    return contextPath + "app_direct/" + appName + "/";
  }
  
  private static ProxySpec convert(ShinyProxySpec from) {
    ProxySpec to = new ProxySpec();
    to.setId(from.getId());
    to.setDisplayName(from.getDisplayName());
    to.setDescription(from.getDescription());
    to.setLogoURL(from.getLogoURL());
    to.setDefaultTutorialLink(from.getDefaultTutorialLink());
    if (from.getKubernetesPodPatches() != null) {
      try {
        to.setKubernetesPodPatches(from.getKubernetesPodPatches());
      } catch (Exception e) {
        throw new IllegalArgumentException(String.format("Configuration error: spec with id '%s' has invalid kubernetes-pod-patches", from.getId()));
      }
    }
    to.setKubernetesAdditionalManifests(from.getKubernetesAdditionalManifests());
    to.setSubApps(from.getSubApps());
    
    if (from.getAccessGroups() != null && from.getAccessGroups().length > 0) {
      ProxyAccessControl acl = new ProxyAccessControl();
      acl.setGroups(from.getAccessGroups());
      to.setAccessControl(acl);
    }
    
    ContainerSpec cSpec = new ContainerSpec();
    cSpec.setImage(from.getContainerImage());
    cSpec.setCmd(from.getContainerCmd());
    
    Map<String, String> env = from.getContainerEnv();
    if (env == null) {
      env = new HashMap<>();
    }
    
    env.put("SHINYPROXY_PUBLIC_PATH", getPublicPath(from.getId()));
    cSpec.setEnv(env);
    
    cSpec.setEnvFile(from.getContainerEnvFile());
    cSpec.setNetwork(from.getContainerNetwork());
    cSpec.setNetworkConnections(from.getContainerNetworkConnections());
    cSpec.setDns(from.getContainerDns());
    cSpec.setVolumes(from.getContainerVolumes());
    cSpec.setMemoryRequest(from.getContainerMemoryRequest());
    cSpec.setMemoryLimit(from.getContainerMemoryLimit());
    cSpec.setCpuRequest(from.getContainerCpuRequest());
    cSpec.setCpuLimit(from.getContainerCpuLimit());
    cSpec.setPrivileged(from.isContainerPrivileged());
    cSpec.setLabels(from.getLabels());
    
    Map<String, Integer> portMapping = new HashMap<>();
    if (from.getPort() > 0) {
      portMapping.put("default", from.getPort());
    } else {
      portMapping.put("default", 3838);
    }
    cSpec.setPortMapping(portMapping);
    
    to.setContainerSpecs(Collections.singletonList(cSpec));
    
    return to;
  }
  
  public static class ShinyProxySpec {
    
    private String id;
    private String displayName;
    private String description;
    private String logoURL;
    
    private String containerImage;
    private String[] containerCmd;
    private Map<String,String> containerEnv;
    private String containerEnvFile;
    private String containerNetwork;
    private String[] containerNetworkConnections;
    private String[] containerDns;
    private String[] containerVolumes;
    private String containerMemoryRequest;
    private String containerMemoryLimit;
    private String containerCpuRequest;
    private String containerCpuLimit;
    private boolean containerPrivileged;
    private String kubernetesPodPatches;
    private List<String> kubernetesAdditionalManifests = new ArrayList<>();
    
    private List<SubApplicationSpec> subApps;
    private String defaultTutorialLink;
    private Map<String,String> labels;
    
    private int port;
    private String[] accessGroups;
    
    public String getId() {
      return id;
    }
    
    public void setId(String id) {
      this.id = id;
    }
    
    public String getDisplayName() {
      return displayName;
    }
    
    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }
    
    public String getDescription() {
      return description;
    }
    
    public void setDescription(String description) {
      this.description = description;
    }
    
    public String getLogoURL() {
      return logoURL;
    }
    
    public void setLogoURL(String logoURL) {
      this.logoURL = logoURL;
    }
    
    public String getContainerImage() {
      return containerImage;
    }
    
    public void setContainerImage(String containerImage) {
      this.containerImage = containerImage;
    }
    
    public String[] getContainerCmd() {
      return containerCmd;
    }
    
    public void setContainerCmd(String[] containerCmd) {
      this.containerCmd = containerCmd;
    }
    
    public Map<String, String> getContainerEnv() {
      return containerEnv;
    }
    
    public void setContainerEnv(Map<String, String> containerEnv) {
      this.containerEnv = containerEnv;
    }
    
    public String getContainerEnvFile() {
      return containerEnvFile;
    }
    
    public void setContainerEnvFile(String containerEnvFile) {
      this.containerEnvFile = containerEnvFile;
    }
    
    public String getContainerNetwork() {
      return containerNetwork;
    }
    
    public void setContainerNetwork(String containerNetwork) {
      this.containerNetwork = containerNetwork;
    }
    
    public String[] getContainerNetworkConnections() {
      return containerNetworkConnections;
    }
    
    public void setContainerNetworkConnections(String[] containerNetworkConnections) {
      this.containerNetworkConnections = containerNetworkConnections;
    }
    
    public String[] getContainerDns() {
      return containerDns;
    }
    
    public void setContainerDns(String[] containerDns) {
      this.containerDns = containerDns;
    }
    
    public String[] getContainerVolumes() {
      return containerVolumes;
    }
    
    public void setContainerVolumes(String[] containerVolumes) {
      this.containerVolumes = containerVolumes;
    }
    
    public String getContainerMemoryRequest() {
      return containerMemoryRequest;
    }
    
    public void setContainerMemoryRequest(String containerMemoryRequest) {
      this.containerMemoryRequest = containerMemoryRequest;
    }
    
    public String getContainerMemoryLimit() {
      return containerMemoryLimit;
    }
    
    public void setContainerMemoryLimit(String containerMemoryLimit) {
      this.containerMemoryLimit = containerMemoryLimit;
    }
    
    public String getContainerCpuRequest() {
      return containerCpuRequest;
    }
    
    public void setContainerCpuRequest(String containerCpuRequest) {
      this.containerCpuRequest = containerCpuRequest;
    }
    
    public String getContainerCpuLimit() {
      return containerCpuLimit;
    }
    
    public void setContainerCpuLimit(String containerCpuLimit) {
      this.containerCpuLimit = containerCpuLimit;
    }
    
    public boolean isContainerPrivileged() {
      return containerPrivileged;
    }
    
    public void setContainerPrivileged(boolean containerPrivileged) {
      this.containerPrivileged = containerPrivileged;
    }
    
    public Map<String, String> getLabels() {
      return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
      this.labels = labels;
    }
    
    public int getPort() {
      return port;
    }
    
    public void setPort(int port) {
      this.port = port;
    }
    
    public String[] getAccessGroups() {
      return accessGroups;
    }
    
    public void setAccessGroups(String[] accessGroups) {
      this.accessGroups = accessGroups;
    }
    
    public String getKubernetesPodPatches() {
      return kubernetesPodPatches;
    }
    
    public void setKubernetesPodPatches(String kubernetesPodPatches) {
      this.kubernetesPodPatches = kubernetesPodPatches;
    }
    
    public void setKubernetesAdditionalManifests(List<String> manifests) {
      this.kubernetesAdditionalManifests = manifests;
    }
    
    public List<String> getKubernetesAdditionalManifests() {
      return kubernetesAdditionalManifests;
    }
    
    public List<SubApplicationSpec> getSubApps() {
      return subApps;
    }
    
    public void setSubApps(List<SubApplicationSpec> subApps) {
      this.subApps = subApps;
    }
    
    public String getDefaultTutorialLink() {
      return defaultTutorialLink;
    }
    
    public void setDefaultTutorialLink(String defaultTutorialLink) {
      this.defaultTutorialLink = defaultTutorialLink;
    }
  }
  
  public static ProxySpec getProxySpec(ProxySpec to, ContainerSpec cSpec, Map<String, String> labels, int port) {
    cSpec.setLabels(labels);
    
    Map<String, Integer> portMapping = new HashMap<>();
    if (port > 0) {
      portMapping.put("default", port);
    } else {
      portMapping.put("default", 3838);
    }
    cSpec.setPortMapping(portMapping);
    
    to.setContainerSpecs(Collections.singletonList(cSpec));
    
    return to;
  }
}
