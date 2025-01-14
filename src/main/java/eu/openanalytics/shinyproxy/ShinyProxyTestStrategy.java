/**
 * ShinyProxy
 * <p>
 * Copyright (C) 2016-2021 Open Analytics
 * <p>
 * ===========================================================================
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 * <p>
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * This component tests the responsiveness of Shiny containers by making an HTTP GET request to the container's published port (default 3838).
 * If this request does not receive a 200 (OK) response within a configured time limit, the container is considered to be unresponsive.
 */
@Component
@Primary
public class ShinyProxyTestStrategy implements IProxyTestStrategy {
  
  private final Environment environment;
  private final Logger log = LogManager.getLogger(ShinyProxyTestStrategy.class);
  
  public ShinyProxyTestStrategy(Environment environment) {
    this.environment = environment;
  }
  
  private static boolean retry(IntPredicate job, int tries, int waitTime, boolean retryOnException) {
    boolean retVal = false;
    RuntimeException exception = null;
    for (int currentTry = 1; currentTry <= tries; currentTry++) {
      try {
        if (job.test(currentTry)) {
          retVal = true;
          exception = null;
          break;
        }
      } catch (RuntimeException e) {
        if (retryOnException) exception = e;
        else throw e;
      }
      try {
        Thread.sleep(waitTime);
      } catch (InterruptedException ignore) {
      }
    }
    if (exception == null) return retVal;
    else throw exception;
  }
  
  @Override
  public boolean testProxy(Proxy proxy) {
    
    int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.container-wait-time", "20000"));
    int waitMs = Math.min(2000, totalWaitMs);
    int maxTries = totalWaitMs / waitMs;
    int timeoutMs = Integer.parseInt(environment.getProperty("proxy.container-wait-timeout", "5000"));
    
    if (proxy.getTargets().isEmpty()) return false;
    URI targetURI = proxy.getTargets().values().iterator().next();
    
    return retry(i -> {
      try {
        if (proxy.getStatus() == ProxyStatus.Stopping || proxy.getStatus() == ProxyStatus.Stopped) return true;
        URL testURL = new URL(targetURI.toString());
        HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
        connection.setConnectTimeout(timeoutMs);
        connection.setInstanceFollowRedirects(false);
        int responseCode = connection.getResponseCode();
        if (responseCode < 400) return true;
      } catch (Exception e) {
        if (i > 1)
          log.warn(String.format("Container unresponsive, trying again (%d/%d): %s", i, maxTries, targetURI));
      }
      return false;
    }, maxTries, waitMs, false);
  }
}
