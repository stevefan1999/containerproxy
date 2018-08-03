/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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
package eu.openanalytics.containerproxy.backend.strategy;

import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.Proxy;

/**
 * Defines a strategy for creating a concrete mapping (a piece of URL) using the mapping key provided by the container spec.
 *
 * For example, if the mapping spec is { "mymapping": 8080 } then the mapping strategy may be computed as
 * "/containerId/mymapping" which would map into port 8080 of the container.
 */
public interface IProxyTargetMappingStrategy {

	public String createMapping(String mappingKey, Container container, Proxy proxy);

}