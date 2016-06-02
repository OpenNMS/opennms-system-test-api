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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opennms.test.system.api.NewTestEnvironment.ContainerAlias;

import com.google.common.collect.Maps;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerInfo;

/**
 * Used to point to any existing TestEnvironment that was set up with the
 * NewTestEnvironment(true).
 *
 * This is particularly useful when developing tests, since the
 * containers do not need to be recreated every time.
 *
 * We assume that there is a single set of containers created with
 * the image names we use.
 *
 * @author jwhite
 */
public class ExistingTestEnvironment extends AbstractTestEnvironment implements TestEnvironment {

    private DockerClient docker;

    private final Map<ContainerAlias, ContainerInfo> containerInfo = Maps.newHashMap();

    @Override
    protected void before() throws Throwable {
        // Invert the map
        Map<String, ContainerAlias> aliasesByImage = Maps.newHashMap();
        for (Entry<ContainerAlias, String> entry : NewTestEnvironment.IMAGES_BY_ALIAS.entrySet()) {
            aliasesByImage.put(entry.getValue(), entry.getKey());
        }

        docker = DefaultDockerClient.fromEnv().build();
        for (final Container container : docker.listContainers()) {
            final ContainerAlias alias = aliasesByImage.get(container.image());
            if (alias == null) {
                continue;
            }
            containerInfo.put(alias, docker.inspectContainer(container.id()));
        }
    }

    @Override
    protected void after(final boolean didFail, final Throwable failure) {
        if (docker == null) {
            return;
        }
        docker.close();
        docker = null;
    }

    @Override
    public ContainerInfo getContainerInfo(ContainerAlias alias) {
        return containerInfo.get(alias);
    }

    @Override
    public Set<ContainerAlias> getContainerAliases() {
        return containerInfo.keySet();
    }

    @Override
    public DockerClient getDockerClient() {
        return docker;
    }
}
