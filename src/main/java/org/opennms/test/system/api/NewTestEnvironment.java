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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.cxf.helpers.FileUtils;
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
     * The name of this test environment.
     */
    private final String name;

    /**
     * Set if the containers should be kept running after the tests complete
     * (regardless of whether or not they were successful)
     */
    private final boolean skipTearDown;

    /**
     * The location of the files to be overlayed into /opt/opennms.
     */
    private Path overlayDirectory;

    /**
     * A collection of containers that should be started by default
     */
    private Collection<ContainerAlias> start;

    /**
     * Keeps track of the IDs for all the created containers so we can
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

    public NewTestEnvironment(final String name, final boolean skipTearDown, final Path overlayDirectory, final Collection<ContainerAlias> containers) {
        this.name = name;
        this.skipTearDown = skipTearDown;
        this.overlayDirectory = overlayDirectory;
        this.start = containers;
    }

    @Override
    protected void before() throws Throwable {
        docker = DefaultDockerClient.fromEnv().build();

        LOG.debug("Starting containers: {}", start);

        spawnPostgres();
        spawnOpenNMS();
        spawnSnmpd();
        spawnTomcat();
        spawnMinion();

        LOG.debug("Waiting for containers to be ready: {}", start);

        waitForPostgres();
        waitForOpenNMS();
        waitForSnmpd();
        waitForTomcat();
        waitForMinion();
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
            containerInfoByAlias.clear();
            createdContainerIds.clear();
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

    /**
     * Spawns the PostgreSQL container.
     */
    private void spawnPostgres() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.POSTGRES;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        final Builder builder = HostConfig.builder()
                .publishAllPorts(true);
        spawnContainer(alias, builder);
    }

    /**
     * Spawns the OpenNMS container, linked to PostgreSQL.
     */
    private void spawnOpenNMS() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.OPENNMS;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        final Path overlayRoot = Paths.get("target", "overlays", this.description.getTestClass().getSimpleName()).toAbsolutePath();
        if (overlayRoot.toFile().exists()) {
            FileUtils.removeDir(overlayRoot.toFile());
        }

        final Path opennmsOverlay = overlayRoot.resolve("opennms-overlay");
        final Path opennmsLogs = overlayRoot.resolve("opennms-logs");
        final Path opennmsKarafLogs = overlayRoot.resolve("opennms-karaf-logs");

        Files.createDirectories(opennmsOverlay);
        Files.createDirectories(opennmsLogs);
        Files.createDirectories(opennmsKarafLogs);

        Files.find(this.overlayDirectory, 10, (path, attr) -> {
            return path.toFile().isFile();
        }).forEach(path -> {
            final Path relative = Paths.get(this.overlayDirectory.toFile().toURI().relativize(path.toFile().toURI()).getPath());
            final Path to = Paths.get(opennmsOverlay.toString(), relative.toString());
            LOG.debug("Copying {} to {}", path.toAbsolutePath(), to.toAbsolutePath());
            try {
                Files.createDirectories(to.getParent());
                Files.copy(path.toAbsolutePath(), to.toAbsolutePath());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        });

        final List<String> binds = new ArrayList<>();
        binds.add(opennmsOverlay.toString() + ":/opennms-docker-overlay");
        binds.add(opennmsLogs.toString() + ":/var/log/opennms");
        binds.add(opennmsKarafLogs.toString() + ":/opt/opennms/data/log");

        final Builder builder = HostConfig.builder()
                .privileged(true)
                .publishAllPorts(true)
                .links(String.format("%s:postgres", containerInfoByAlias.get(ContainerAlias.POSTGRES).name()))
                .binds(binds);

        spawnContainer(alias, builder);
    }

    /**
     * Spawns the Net-SNMP container.
     */
    private void spawnSnmpd() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.SNMPD;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        spawnContainer(alias, HostConfig.builder());
    }

    /**
     * Spawns the Tomcat container.
     */
    private void spawnTomcat() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.TOMCAT;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        spawnContainer(alias, HostConfig.builder());
    }

    /**
     * Spawns the Minion container, linked to OpenNMS, Net-SNMP and Tomcat.
     */
    private void spawnMinion() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.MINION;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        final List<String> links = Lists.newArrayList();
        links.add(String.format("%s:opennms", containerInfoByAlias.get(ContainerAlias.OPENNMS).name()));
        links.add(String.format("%s:snmpd", containerInfoByAlias.get(ContainerAlias.SNMPD).name()));
        links.add(String.format("%s:tomcat", containerInfoByAlias.get(ContainerAlias.TOMCAT).name()));

        final Builder builder = HostConfig.builder()
                .publishAllPorts(true)
                .links(links);
        spawnContainer(alias, builder);
    }

    private boolean isEnabled(final ContainerAlias alias) {
        return start.contains(alias);
    }

    private boolean isSpawned(final ContainerAlias alias) {
        return !containerInfoByAlias.containsKey(alias);
    }

    /**
     * Spawns a container.
     */
    private void spawnContainer(final ContainerAlias alias, final Builder hostConfigBuilder) throws DockerException, InterruptedException, IOException {
        final ContainerConfig containerConfig = ContainerConfig.builder()
                .image(IMAGES_BY_ALIAS.get(alias))
                .hostConfig(hostConfigBuilder.build())
                .hostname(this.name + ".local")
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
     * Blocks until we can connect to the PostgreSQL data port.
     */
    private void waitForPostgres() {
        final ContainerAlias alias = ContainerAlias.POSTGRES;
        if (!isEnabled(alias)) {
            return;
        }

        final InetSocketAddress postgresAddr = getServiceAddress(alias, 5432);
        final Callable<Boolean> isConnected = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    final Socket socket = new Socket(postgresAddr.getAddress(), postgresAddr.getPort());
                    socket.setReuseAddress(true);
                    final InputStream is = socket.getInputStream();
                    final OutputStream os = socket.getOutputStream();
                    os.write("¯\\_(ツ)_/¯\n".getBytes());
                    os.close();
                    is.close();
                    socket.close();
                    // good enough, not gonna try speaking the PostgreSQL protocol
                    return true;
                } catch (final Throwable t) {
                    LOG.debug("PostgreSQL connect failed: " + t.getMessage());
                    return null;
                }
            }
        };
        LOG.info("************************************************************");
        LOG.info("Waiting for PostgreSQL service @ {}.", postgresAddr);
        LOG.info("************************************************************");
        await().atMost(5, MINUTES).pollInterval(10, SECONDS).until(isConnected, is(notNullValue()));
    }

    /**
     * Blocks until the REST and Karaf Shell services are available.
     */
    private void waitForOpenNMS() throws Exception {
        final ContainerAlias alias = ContainerAlias.OPENNMS;
        if (!isEnabled(alias)) {
            return;
        }

        final InetSocketAddress httpAddr = getServiceAddress(alias, 8980);
        final RestClient restClient = new RestClient(httpAddr);
        final Callable<String> getDisplayVersion = new Callable<String>() {
            @Override
            public String call() throws Exception {
                try {
                    final String displayVersion = restClient.getDisplayVersion();
                    LOG.info("Connected to OpenNMS version {}", displayVersion);
                    return displayVersion;
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
        await().atMost(5, MINUTES).pollInterval(10, SECONDS).until(getDisplayVersion, is(notNullValue()));
        LOG.info("************************************************************");
        LOG.info("OpenNMS's REST service is online.");
        LOG.info("************************************************************");

        final InetSocketAddress sshAddr = getServiceAddress(alias, 8101);
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
     * TODO: Blocks until the SNMP daemon is available.
     */
    private void waitForSnmpd() throws Exception {
        final ContainerAlias alias = ContainerAlias.SNMPD;
        if (!isEnabled(alias)) {
            return;
        }

    }

    /**
     * TODO: Blocks until the Tomcat HTTP daemon is available.
     */
    private void waitForTomcat() throws Exception {
        final ContainerAlias alias = ContainerAlias.TOMCAT;
        if (!isEnabled(alias)) {
            return;
        }

    }

    /**
     * Blocks until the Karaf Shell service is available.
     */
    private void waitForMinion() throws Exception {
        final ContainerAlias alias = ContainerAlias.MINION;
        if (!isEnabled(alias)) {
            return;
        }

        final InetSocketAddress sshAddr = getServiceAddress(alias, 8201);
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
