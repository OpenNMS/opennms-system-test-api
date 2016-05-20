/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2016 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/
package org.opennms.test.system.api;

import java.net.InetSocketAddress;
import java.util.List;

import org.opennms.test.system.api.NewTestSystem.ContainerAlias;
import org.opennms.test.system.api.junit.ExternalResourceRule;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.PortBinding;

public abstract class AbstractTestSystem extends ExternalResourceRule implements TestSystem {

    public abstract DockerClient getDockerClient();

    @Override
    public InetSocketAddress getServiceAddress(ContainerAlias alias, int port) {
        return getServiceAddress(alias, port, "tcp");
    }

    @Override
    public InetSocketAddress getServiceAddress(ContainerAlias alias, int port, String type) {
        final ContainerInfo info = getContainerInfo(alias);
        if (info == null) {
            throw new IllegalArgumentException(String.format("No container found with alias: %s", alias));
        }
        final String portKey = port + "/" + type;
        final List<PortBinding> bindings = info.networkSettings().ports().get(portKey);
        if (bindings == null) {
            throw new IllegalArgumentException(String.format("No bindings found for port %s on alias: %s",
                    portKey, alias));
        }
        final PortBinding binding = bindings.iterator().next();
        final String host = "0.0.0.0".equals(binding.hostIp()) ? getDockerClient().getHost() : binding.hostIp();
        return new InetSocketAddress(host, Integer.valueOf(binding.hostPort()));
    }
}
