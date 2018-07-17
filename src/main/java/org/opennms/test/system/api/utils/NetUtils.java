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
package org.opennms.test.system.api.utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for testing network connectivity.
 *
 * @author jwhite
 */
public class NetUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NetUtils.class);

    public static InetAddress getNonLocalAddress() throws UnknownHostException {
        final Collection<InetAddress> addrs = NetUtils.getNonLocalAddresses();
        if (addrs.isEmpty()) {
            LOG.warn("No non-loopback, non-link-local addresses found. Falling back to InetAddress.getLocalHost();");
            return InetAddress.getLocalHost();
        }
        final InetAddress ret = addrs.iterator().next();
        if (addrs.size() > 1) {
            LOG.warn("Found more than one non-loopback, non-link-local address ({}). Returning the first match.", addrs, ret);
        }
        return ret;
    }

    public static Collection<InetAddress> getNonLocalAddresses() throws UnknownHostException {
        final Set<InetAddress> addrs = new LinkedHashSet<>();
        try {
            final ArrayList<NetworkInterface> list = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (final NetworkInterface iface : list) {
                if (!iface.isLoopback() && !iface.isPointToPoint() && iface.isUp()) {
                    for (final InetAddress inetAddr : Collections.list(iface.getInetAddresses())) {
                        if (!inetAddr.isLoopbackAddress() && !inetAddr.isLinkLocalAddress()) {
                            addrs.add(inetAddr);
                        }
                    }
                }
            }
            LOG.debug("Found addresses: {}", addrs);
            return addrs;
        } catch (final Exception e) {
            LOG.warn("Failed to determine primary interface.", e);
        }
        return Collections.emptyList();
    }

    public static boolean isTcpPortOpen(int port) {
        return isTcpPortOpen(new InetSocketAddress("127.0.0.1", port));
    }

    public static boolean isTcpPortOpen(InetSocketAddress addr) {
        try (Socket socket = new Socket()) {
            socket.connect(addr, 100);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static Callable<Boolean> isTcpPortOpenCallable(final int port) {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return isTcpPortOpen(port);
            }
        };
    }
}
