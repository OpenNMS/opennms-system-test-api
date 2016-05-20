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

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.opennms.test.system.api.utils.RestClient;
import org.opennms.test.system.api.utils.SshClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Bind;
import com.spotify.docker.client.messages.HostConfig.Builder;

/**
 * Spawns and configures a collection of Docker containers running the Minion TestEnvironment.
 *
 * In particular, this is composed of:
 *  1) postgres: An instance of PostgreSQL 
 *  2) opennms: An instance of OpenNMS
 *  3) minion: An instance of Minion
 *  4) snmpd: An instance of Net-SNMP (used to test SNMP support)
 *  5) tomcat: An instance of Tomcat (used to test JMX support)
 *
 * @author jwhite
 */
public class NewTestEnvironment extends AbstractTestEnvironment implements TestEnvironment {

    private static final Logger LOG = LoggerFactory.getLogger(NewTestEnvironment.class);

    /**
     * Aliases used to refer to the containers within the tests
     * Note that these are not the container IDs or names
     */
    public static enum ContainerAlias {
        POSTGRES,
        OPENNMS,
        MINION,
        SNMPD,
        TOMCAT
    }

    /**
     * Mapping from the alias to the Docker image name
     */
    public static final ImmutableMap<ContainerAlias, String> IMAGES_BY_ALIAS =
            new ImmutableMap.Builder<ContainerAlias, String>()
            .put(ContainerAlias.POSTGRES, "postgres:9.5.1")
            .put(ContainerAlias.OPENNMS, "stests/opennms")
            .put(ContainerAlias.MINION, "stests/minion")
            .put(ContainerAlias.SNMPD, "stests/snmpd")
            .put(ContainerAlias.TOMCAT, "stests/tomcat")
            .build();

    /**
     * Set if the containers should be kept running after the tests complete
     * (regardless of whether or not they were successful)
     */
    private final boolean skipTearDown;

    /**
     * A path to mount as the "opennms-docker-overlay" directory inside the
     * OpenNMS container.
     */
    private Path overlayPath;

    /**
     * A collection of containers that should be started by default
     */
    private Collection<ContainerAlias> start;

    /**
     * Keeps track of the IDs for all the created containers sp we can
     * (possibly) tear them down later
     */
    private final Set<String> createdContainerIds = Sets.newHashSet();

    /**
     * Keep track of container meta-data
     */
    private final Map<ContainerAlias, ContainerInfo> containerInfoByAlias = Maps.newHashMap();

    /**
     * The Docker daemon client
     */
    private DockerClient docker;

    public NewTestEnvironment() {
        this(false);
    }

    public NewTestEnvironment(boolean skipTearDown) {
        this(skipTearDown, null, Lists.newArrayList(ContainerAlias.values()));
    }

    public NewTestEnvironment(boolean skipTearDown, final Path overlay, Collection<ContainerAlias> containers) {
        this.skipTearDown = skipTearDown;
        this.overlayPath = overlay;
        this.start = containers;
    }

    @Override
    protected void before() throws Throwable {
        docker = DefaultDockerClient.fromEnv().build();

        LOG.debug("Starting containers: {}", start);
        for (final ContainerAlias alias : start) {
            final HostConfig config = getHostConfig(alias);
            spawnContainer(alias, config);
        }

        for (final ContainerAlias alias : start) {
            waitFor(alias).call();
        }
    };

    @Override
    protected void after(boolean didFail) {
        if (docker == null) {
            LOG.warn("Docker instance is null. Skipping tear down.");
            return;
        }

        /* TODO: Gathering the log files can cause the tests to hang indefinitely.
        // Ideally, we would only gather the logs and container output
        // when we fail, but we can't detect this when using @ClassRules
        ContainerInfo opennmsContainerInfo = containerInfoByAlias.get(ContainerAlias.OPENNMS);
        if (opennmsContainerInfo != null) {
            LOG.info("Gathering OpenNMS logs...");
            Path destination = Paths.get("target/opennms.logs.tar");
            try (
                    final InputStream in = docker.copyContainer(opennmsContainerInfo.id(), "/opt/opennms/logs");
            ) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (DockerException|InterruptedException|IOException e) {
                LOG.warn("Failed to copy the logs directory from the Dominion container.", e);
            }

            destination = Paths.get("target/opennms.karaf.logs.tar");
            try (
                 final InputStream in = docker.copyContainer(opennmsContainerInfo.id(), "/opt/opennms/data/log");
                 ) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (DockerException|InterruptedException|IOException e) {
                LOG.warn("Failed to copy the data/log directory from the Dominion container.", e);
            }
        } else {
            LOG.warn("No OpenNMS container provisioned. Logs won't be copied.");
        }

        // Ideally, we would only gather the logs and container output
        // when we fail, but we can't detect this when using @ClassRules
        opennmsContainerInfo = containerInfoByAlias.get(ContainerAlias.MINION);
        if (opennmsContainerInfo != null) {
            LOG.info("Gathering Minion logs...");
            final Path destination = Paths.get("target/minion.logs.tar");
            try (
                 final InputStream in = docker.copyContainer(opennmsContainerInfo.id(), "/opt/minion/data/log");
                 ) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (DockerException|InterruptedException|IOException e) {
                LOG.warn("Failed to copy the logs directory from the Minion container.", e);
            }
        } else {
            LOG.warn("No Minion container provisioned. Logs won't be copied.");
        }
         */

        LOG.info("************************************************************");
        LOG.info("Gathering container output...");
        LOG.info("************************************************************");
        for (String containerId : createdContainerIds) {
            try {
                LogStream logStream = docker.logs(containerId, LogsParam.stdout(), LogsParam.stderr());
                LOG.info("************************************************************");
                LOG.info("Start of stdout/stderr for {}:", containerId);
                LOG.info("************************************************************");
                LOG.info(logStream.readFully());
                LOG.info("************************************************************");
                LOG.info("End of stdout/stderr for {}:", containerId);
                LOG.info("************************************************************");
            } catch (DockerException | InterruptedException e) {
                LOG.warn("Failed to get stdout/stderr for container {}.", e);
            }
        }

        if (!skipTearDown) {
            // Kill and remove all of the containers we created
            for (String containerId : createdContainerIds) {
                try {
                    LOG.info("************************************************************");
                    LOG.info("Killing and removing container with id: {}", containerId);
                    LOG.info("************************************************************");
                    docker.killContainer(containerId);
                    docker.removeContainer(containerId);
                } catch (Exception e) {
                    LOG.error("************************************************************");
                    LOG.error("Failed to kill and/or remove container with id: {}", containerId, e);
                    LOG.error("************************************************************");
                }
            }
        } else {
            LOG.info("Skipping tear down.");
        }

        docker.close();
    };

    @Override
    public Set<ContainerAlias> getContainerAliases() {
        return containerInfoByAlias.keySet();
    }

    @Override
    public ContainerInfo getContainerInfo(final ContainerAlias alias) {
        return containerInfoByAlias.get(alias);
    }

    private HostConfig getHostConfig(final ContainerAlias container) {
        switch(container) {
        case POSTGRES: {
            return HostConfig.builder()
                    .publishAllPorts(true)
                    .build();
        }
        case OPENNMS: {
            final Builder builder = HostConfig.builder()
                    .privileged(true)
                    .publishAllPorts(true)
                    .links(String.format("%s:postgres", containerInfoByAlias.get(ContainerAlias.POSTGRES).name()));

            if (this.overlayPath != null) {
                builder.binds(Bind.from(overlayPath.toAbsolutePath().toString()).to("/opennms-docker-overlay").build());
            }

            return builder.build();
        }
        case MINION: {
            final List<String> links = Lists.newArrayList();
            links.add(String.format("%s:opennms", containerInfoByAlias.get(ContainerAlias.OPENNMS).name()));
            links.add(String.format("%s:snmpd", containerInfoByAlias.get(ContainerAlias.SNMPD).name()));
            links.add(String.format("%s:tomcat", containerInfoByAlias.get(ContainerAlias.TOMCAT).name()));

            return HostConfig.builder()
                    .publishAllPorts(true)
                    .links(links)
                    .build();
        }
        default: {
            return HostConfig.builder().build();
        }
        }
    }

    private Callable<Void> waitFor(final ContainerAlias alias) {
        return new Callable<Void>() {
            @Override public Void call() throws Exception {
                LOG.debug("waitFor({})", alias);
                switch(alias) {
                    case OPENNMS: {
                        waitForOpenNMS();
                        break;
                    }
                    case MINION: {
                        waitForMinion();
                        break;
                    }
                    default: {
                        break;
                    }
                }
                return null;
            }
        };
    }

    /**
     * Spawns a container.
     */
    private void spawnContainer(ContainerAlias alias, HostConfig hostConfig) throws DockerException, InterruptedException {
        final ContainerConfig containerConfig = ContainerConfig.builder()
                .image(IMAGES_BY_ALIAS.get(alias))
                .hostConfig(hostConfig)
                .build();

        final ContainerCreation containerCreation = docker.createContainer(containerConfig);
        final String containerId = containerCreation.id();
        createdContainerIds.add(containerId);

        docker.startContainer(containerId);

        final ContainerInfo containerInfo = docker.inspectContainer(containerId);
        LOG.info("************************************************************");
        LOG.info("{} container info: {}", alias, containerId);
        LOG.info("************************************************************");
        if (!containerInfo.state().running()) {
            throw new IllegalStateException("Could not start the " + alias + " container");
        }

        containerInfoByAlias.put(alias, containerInfo);
    }

    /**
     * Blocks until the REST and Karaf Shell services are available.
     */
    private void waitForOpenNMS() throws Exception {
        final InetSocketAddress httpAddr = getServiceAddress(ContainerAlias.OPENNMS, 8980);
        final RestClient restClient = new RestClient(httpAddr);
        final Callable<String> getDisplayVersion = new Callable<String>() {
            @Override
            public String call() throws Exception {
                try {
                    return restClient.getDisplayVersion();
                } catch (Throwable t) {
                    LOG.debug("Version lookup failed: " + t.getMessage());
                    return null;
                }
            }
        };

        LOG.info("************************************************************");
        LOG.info("Waiting for REST service @ {}.", httpAddr);
        LOG.info("************************************************************");
        // TODO: It's possible that the OpenNMS server doesn't start if there are any
        // problems in $OPENNMS_HOME/etc. Instead of waiting the whole 5 minutes and timing out
        // we should also poll the status of the container, so we can fail sooner.
        await().atMost(5, MINUTES).pollInterval(15, SECONDS).until(getDisplayVersion, is(notNullValue()));
        LOG.info("************************************************************");
        LOG.info("OpenNMS's REST service is online.");
        LOG.info("************************************************************");

        final InetSocketAddress sshAddr = getServiceAddress(ContainerAlias.OPENNMS, 8101);
        LOG.info("************************************************************");
        LOG.info("Waiting for SSH service @ {}.", sshAddr);
        LOG.info("************************************************************");
        await().atMost(2, MINUTES).pollInterval(5, SECONDS).until(SshClient.canConnectViaSsh(sshAddr, "admin", "admin"));
        listFeatures(sshAddr, false);
        LOG.info("************************************************************");
        LOG.info("OpenNMS's Karaf Shell is online.");
        LOG.info("************************************************************");
    }

    /**
     * Blocks until the Karaf Shell service is available.
     */
    private void waitForMinion() throws Exception {
        final InetSocketAddress sshAddr = getServiceAddress(ContainerAlias.MINION, 8201);
        LOG.info("************************************************************");
        LOG.info("Waiting for SSH service for Karaf instance @ {}.", sshAddr);
        LOG.info("************************************************************");
        await().atMost(2, MINUTES).pollInterval(5, SECONDS).until(SshClient.canConnectViaSsh(sshAddr, "admin", "admin"));
        listFeatures(sshAddr, true);
    }

    private static void listFeatures(InetSocketAddress sshAddr, boolean karaf4) throws Exception {
        try (
                final SshClient sshClient = new SshClient(sshAddr, "admin", "admin");
                ) {
            PrintStream pipe = sshClient.openShell();
            if (karaf4) {
                pipe.println("feature:list -i");
            } else {
                pipe.println("features:list -i");
            }
            pipe.println("list");
            pipe.println("logout");
            try {
                await().atMost(2, MINUTES).until(sshClient.isShellClosedCallable());
            } finally {
                LOG.info("Features installed:\n{}", sshClient.getStdout());
            }
        }
    }

    @Override
    public DockerClient getDockerClient() {
        return docker;
    }
}
