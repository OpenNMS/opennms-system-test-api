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
import java.util.Set;

import org.junit.rules.TestRule;
import org.opennms.test.system.api.NewTestEnvironment.ContainerAlias;

import com.spotify.docker.client.messages.ContainerInfo;

/**
 * The system test rule is responsible for ensuring that a valid
 * Minion TestEnvironment is setup before invoking any tests.
 *
 * Further, the test rule must provide all of the details required
 * to communicate with the Minion TestEnvironment, whether it be on the current
 * or a remote host.
 * 
 * @author jwhite
 */
public interface TestEnvironment extends TestRule {

    InetSocketAddress getServiceAddress(ContainerAlias alias, int port);

    InetSocketAddress getServiceAddress(ContainerAlias alias, int port, String type);

    InetSocketAddress getServiceAddress(ContainerInfo alias, int port, String type);

    ContainerInfo getContainerInfo(ContainerAlias alias);

    Set<ContainerAlias> getContainerAliases();

    public static TestEnvironmentBuilder builder() {
        return new TestEnvironmentBuilder();
    }
}
