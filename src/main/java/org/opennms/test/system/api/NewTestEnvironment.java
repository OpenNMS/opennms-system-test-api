/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.cxf.helpers.FileUtils;
import org.opennms.test.system.api.utils.NetUtils;
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
import com.spotify.docker.client.exceptions.ContainerNotFoundException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;

/**
 * Spawns and configures a collection of Docker containers running the Minion TestEnvironment.
 *
 * In particular, this is composed of:
 * <ul>
 *  <li>postgres: An instance of PostgreSQL</li> 
 *  <li>opennms: An instance of OpenNMS</li>
 *  <li>minion: An instance of Minion</li>
 *  <li>sentinel: An instance of Sentinel</li>
 *  <li>snmpd: An instance of Net-SNMP (used to test SNMP support)</li>
 *  <li>tomcat: An instance of Tomcat (used to test JMX support)</li>
 *  <li>kafka: An optional instance of Apache Kafka to test Minion's Kafka support</li>
 *  <li>elasticsearch2: An optional instance of Elasticsearch 2.X</li>
 *  <li>elasticsearch5: An optional instance of Elasticsearch 5.X</li>
 *  <li>elasticsearch6: An optional instance of Elasticsearch 6.X</li>
 * </ul>
 *
 * @author jwhite
 */
public class NewTestEnvironment extends AbstractTestEnvironment implements TestEnvironment {

    private static final Logger LOG = LoggerFactory.getLogger(NewTestEnvironment.class);
    private static final Random m_random = new Random();

    /**
     * Aliases used to refer to the containers within the tests
     * Note that these are not the container IDs or names
     */
    public static enum ContainerAlias {
        ELASTICSEARCH_2,
        ELASTICSEARCH_5,
        ELASTICSEARCH_6,
        KAFKA,
        MINION,
        MINION_SAME_LOCATION,
        MINION_OTHER_LOCATION,
        SENTINEL,
        OPENNMS,
        POSTGRES,
        SNMPD,
        TOMCAT
    }

    @SuppressWarnings("serial")
    public static final EnumMap<ContainerAlias, String> MINION_LOCATIONS = new EnumMap<ContainerAlias, String>(ContainerAlias.class) {{
        this.put(ContainerAlias.MINION, "MINION");
        this.put(ContainerAlias.MINION_SAME_LOCATION, "MINION");
        this.put(ContainerAlias.MINION_OTHER_LOCATION, "BANANA");
    }};

    @SuppressWarnings("serial")
    public static final EnumMap<ContainerAlias, String> MINION_IDS = new EnumMap<ContainerAlias, String>(ContainerAlias.class) {{
        this.put(ContainerAlias.MINION, "00000000-0000-0000-0000-000000ddba11");
        this.put(ContainerAlias.MINION_SAME_LOCATION, "00000000-0000-0000-0000-000000ddba22");
        this.put(ContainerAlias.MINION_OTHER_LOCATION, "00000000-0000-0000-0000-000000ddba33");
    }};

    public static final EnumMap<ContainerAlias, Boolean> INITIALIZED_OVERLAYS = new EnumMap<>(ContainerAlias.class);

    /**
     * Mapping from the alias to the Docker image name
     */
    public static final ImmutableMap<ContainerAlias, String> IMAGES_BY_ALIAS =
            new ImmutableMap.Builder<ContainerAlias, String>()
            .put(ContainerAlias.ELASTICSEARCH_2, "elasticsearch:2-alpine")
            .put(ContainerAlias.ELASTICSEARCH_5, "elasticsearch:5-alpine")
            .put(ContainerAlias.ELASTICSEARCH_6, "docker.elastic.co/elasticsearch/elasticsearch-oss:6.2.3")
            .put(ContainerAlias.KAFKA, "spotify/kafka@sha256:cf8f8f760b48a07fb99df24fab8201ec8b647634751e842b67103a25a388981b")
            .put(ContainerAlias.MINION, "stests/minion")
            .put(ContainerAlias.MINION_SAME_LOCATION, "stests/minion")
            .put(ContainerAlias.MINION_OTHER_LOCATION, "stests/minion")
            .put(ContainerAlias.SENTINEL, "stests/sentinel")
            .put(ContainerAlias.OPENNMS, "stests/opennms")
            .put(ContainerAlias.POSTGRES, "postgres:9.5.1")
            .put(ContainerAlias.SNMPD, "stests/snmpd")
            .put(ContainerAlias.TOMCAT, "stests/tomcat")
            .build();

    /**
     * The name of this test environment.
     */
    private final String name;

    /**
     * Environment properties.
     */
    private final EnumMap<TestEnvironmentProperty,Object> properties;

    /**
     * The location of the files to be overlaid into /opt/opennms.
     */
    private Path overlayDirectory;

    /**
     * The location of the files to be overlaid into /opt/minion.
     */
    private Path minionOverlayDirectory;

    /**
     * The location of the files to be overlaid into /opt/sentinel.
     */
    private Path sentinelOverlayDirectory;

    /**
     * A collection of containers that should be started by default
     */
    private Collection<ContainerAlias> start;

    /**
     * Keeps track of the IDs for all the created containers so we can
     * (possibly) tear them down later
     */
    private final Set<String> createdContainerIds = Sets.newLinkedHashSet();

    /**
     * Keep track of container meta-data
     */
    private final Map<ContainerAlias, ContainerInfo> containerInfoByAlias = Maps.newHashMap();

    /**
     * Keep track of used ports
     */
    private final Map<ContainerAlias, Set<Integer>> ports = Maps.newConcurrentMap();

    /**
     * The Docker daemon client
     */
    private DockerClient docker;

    public NewTestEnvironment(final String name, final EnumMap<TestEnvironmentProperty,Object> properties, final Path overlayDirectory, final Path minionOverlayDirectory, final Collection<ContainerAlias> containers) {
        this.properties = properties;
        this.overlayDirectory = overlayDirectory;
        this.minionOverlayDirectory = minionOverlayDirectory;
        this.start = containers;
        this.name = name;
    }

    public NewTestEnvironment(final String name, final EnumMap<TestEnvironmentProperty,Object> properties, final Path overlayDirectory, final Path minionOverlayDirectory, final Path sentinelOverlayDirectory, final Collection<ContainerAlias> containers) {
        this.properties = properties;
        this.overlayDirectory = overlayDirectory;
        this.minionOverlayDirectory = minionOverlayDirectory;
        this.sentinelOverlayDirectory = sentinelOverlayDirectory;
        this.start = containers;
        this.name = name;
    }

    public String getName() {
        return this.name == null? this.description.getTestClass().getSimpleName() : this.name;
    }

    @Override
    protected void before() throws Throwable {
        docker = DefaultDockerClient.fromEnv().build();

        spawnKafka();
        spawnElasticsearch2();
        spawnElasticsearch5();
        spawnElasticsearch6();

        spawnPostgres();
        waitForPostgres();

        LOG.debug("Starting containers: {}", start);

        spawnOpenNMS();
        spawnSnmpd();
        spawnTomcat();
        spawnMinions();

        LOG.debug("Waiting for other containers to be ready: {}", start);

        waitForOpenNMS();

        // Sentinel may require database access and opennms sets it up on first start,
        // therefore we must wait for opennms, before sentinel can be spawned
        spawnSentinel();

        waitForSnmpd();
        waitForTomcat();
        waitForMinions();
        waitForSentinel();
    };

    @Override
    protected void after(final boolean didFail, final Throwable failure) {
        if (docker == null) {
            LOG.warn("Docker instance is null. Skipping tear down.");
            return;
        }

        if (didFail) {
            LOG.error("Test failed!", failure);
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
                LOG.warn("Failed to copy the logs directory from the Minion container.", e);
            }

            destination = Paths.get("target/opennms.karaf.logs.tar");
            try (
                 final InputStream in = docker.copyContainer(opennmsContainerInfo.id(), "/opt/opennms/data/log");
                 ) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (DockerException|InterruptedException|IOException e) {
                LOG.warn("Failed to copy the data/log directory from the Minion container.", e);
            }
        } else {
            LOG.warn("No OpenNMS container provisioned. Logs won't be copied.");
        }
         */

        /*
         * Logs are in an overlay now.
        // Ideally, we would only gather the logs and container output
        // when we fail, but we can't detect this when using @ClassRules
                final ContainerInfo minionContainerInfo = getContainerInfo(ContainerAlias.MINION);
        if (minionContainerInfo != null && start.contains(ContainerAlias.MINION)) {
            final Path destination = Paths.get("target", getName() + "-minion.logs.tar");
            try (final InputStream in = docker.archiveContainer(minionContainerInfo.id(), "/opt/minion/data/log")) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (DockerException|InterruptedException|IOException e) {
                LOG.warn("Failed to copy the logs directory from the Minion container.", e);
            }
        } else {
            LOG.warn("No Minion container provisioned. Logs won't be copied.");
        }
         */

        /* process the containers in the reverse order of startup */
        final List<String> containerIds = new ArrayList<>(createdContainerIds);
        Collections.reverse(containerIds);

        LOG.info("************************************************************");
        LOG.info("Gathering container output...");
        LOG.info("************************************************************");
        for (final String containerId : containerIds) {
            try {
                LogStream logStream = docker.logs(containerId, LogsParam.stdout(), LogsParam.stderr());
                /*
                LOG.info("************************************************************");
                LOG.info("Start of stdout/stderr for {}:", containerId);
                LOG.info("************************************************************");
                 */
                final ContainerAlias container = getContainerName(containerId);
                final String containerName = container == null? containerId : container.toString().toLowerCase();
                final Path outputPath = Paths.get("target", getName() + "-" + containerName + "-output.log");
                LOG.info("* writing stdout/stderr for {} to {}", containerId, outputPath);
                try (final FileWriter fw = new FileWriter(outputPath.toFile())) {
                    fw.write(logStream.readFully());
                } catch (final IOException e) {
                    LOG.warn("Unable to write to {}", outputPath, e);
                }
                /*
                LOG.info(logStream.readFully());
                LOG.info("************************************************************");
                LOG.info("End of stdout/stderr for {}:", containerId);
                LOG.info("************************************************************");
                 */
            } catch (final DockerException | InterruptedException e) {
                LOG.warn("Failed to get stdout/stderr for container {}.", e);
            }
        }

        if (!(Boolean)properties.getOrDefault(TestEnvironmentProperty.SKIP_TEAR_DOWN, Boolean.FALSE)) {
            for (final String containerId : containerIds) {
                destroyContainer(containerId);
            }

            containerInfoByAlias.clear();
            createdContainerIds.clear();
            ports.clear();
        } else {
            LOG.info("Skipping tear down.");
        }

        docker.close();
    }

    protected void destroyContainer(final String containerId) {
        final ContainerAlias alias = getContainerName(containerId);

        LOG.info("************************************************************");
        LOG.info("Shutting down container {} ({})", alias, containerId);
        LOG.info("************************************************************");

        final Set<InetSocketAddress> containerSockets;

        if (ports.containsKey(alias)) {
            containerSockets = ports.get(alias).stream().map(port -> {
                return getServiceAddress(alias, port);
            }).collect(Collectors.toSet());
        } else {
            containerSockets = Collections.emptySet();
        }

        try {
            await().atMost(5, MINUTES).pollInterval(5, SECONDS).until(() -> {
                try {
                    LOG.debug("Stopping container {} ({})", alias, containerId);
                    docker.stopContainer(containerId, 3);
                } catch (final Exception e) {
                    LOG.error("Attempt to stop container {} ({}) failed.  Will try again.", alias, containerId, e);
                }

                return containerSockets.parallelStream().map(addr -> {
                    if (addr == null) {
                        return true;
                    }
                    return checkSocket(addr);
                }).allMatch(Predicate.isEqual(Boolean.TRUE));
            });
        } catch (final Exception e) {
            LOG.error("************************************************************");
            LOG.error("Failed to shut down container {} ({}).  Giving up.", alias, containerId, e);
            LOG.error("************************************************************");
        }

        LOG.debug("************************************************************");
        LOG.debug("Removing container {} ({})", alias, containerId);
        LOG.debug("************************************************************");
        try {
            docker.removeContainer(containerId);
        } catch (final Exception e) {
            if (!(e instanceof ContainerNotFoundException)) {
                LOG.error("************************************************************");
                LOG.error("Failed to remove container {} ({}).", alias, containerId, e);
                LOG.error("************************************************************");
            }
        }
    };

    private Boolean checkSocket(final InetSocketAddress addr) {
        try (final Socket sock = new Socket()) {
            sock.connect(addr, 50);
        } catch (final Exception e) {
            LOG.debug("Port {} is available!", addr);
            return true;
        }
        LOG.debug("Port {} is still active. :(", addr);
        return false;
    }

    @Override
    public Set<ContainerAlias> getContainerAliases() {
        return containerInfoByAlias.keySet();
    }

    @Override
    public ContainerInfo getContainerInfo(final ContainerAlias alias) {
        return containerInfoByAlias.get(alias);
    }

    private ContainerAlias getContainerName(final String containerId) {
        for (final ContainerAlias alias : start) {
            final ContainerInfo info = containerInfoByAlias.get(alias);
            if (info != null && containerId.equals(info.id())) {
                return alias;
            }
        }
        return null;
    }

    /**
     * Spawns the PostgreSQL container.
     */
    private void spawnPostgres() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.POSTGRES;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        LOG.debug("Starting PostgreSQL");

        final Builder builder = HostConfig.builder()
                .publishAllPorts(true);
        spawnContainer(alias, builder, Collections.emptyList());
    }

    private void spawnElasticsearch2() throws DockerException, InterruptedException, IOException {
        spawnElasticsearch(ContainerAlias.ELASTICSEARCH_2);
    }

    private void spawnElasticsearch5() throws DockerException, InterruptedException, IOException {
        spawnElasticsearch(ContainerAlias.ELASTICSEARCH_5);
    }

    private void spawnElasticsearch6() throws DockerException, InterruptedException, IOException {
        spawnElasticsearch(ContainerAlias.ELASTICSEARCH_6);
    }

    /**
     * Spawns an Elasticsearch container.
     */
    private void spawnElasticsearch(ContainerAlias alias) throws DockerException, InterruptedException, IOException {
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        LOG.debug("Starting Elasticsearch");

        final Builder builder = HostConfig.builder()
                .publishAllPorts(true);
        spawnContainer(alias, builder);
    }

    /**
     * Spawns the Apache Kafka container.
     */
    private void spawnKafka() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.KAFKA;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        LOG.debug("Starting Kafka");

        final Integer zookeeperPort = getAvailablePort(2181, 2681);
        final Integer kafkaPort = getAvailablePort(9092, 9592);

        // Bind Kafka and Zookeeper to the same ports on the Docker host
        final Map<String, List<PortBinding>> portBindings = new HashMap<String, List<PortBinding>>();
        portBindings.put("2181", Collections.singletonList(PortBinding.of("0.0.0.0", zookeeperPort)));
        portBindings.put("9092", Collections.singletonList(PortBinding.of("0.0.0.0", kafkaPort)));

        // Advertise Kafka on the Docker host address
        List<String> env = Arrays.asList(new String[] {
                "ADVERTISED_HOST=" + NetUtils.getNonLocalAddress().getHostAddress(),
                "ADVERTISED_PORT=" + portBindings.get("9092").get(0).hostPort(),
                "NUM_PARTITIONS=" + properties.getOrDefault(TestEnvironmentProperty.KAFKA_PARTITIONS, 10)
        });

        LOG.info("About to start kafka container with the following env settings: {}", env);

        final Builder builder = HostConfig.builder()
                .portBindings(portBindings);
        spawnContainer(alias, builder, env);
    }

    private static int getAvailablePort(final int min, final int max) {
        final Iterator<Integer> it = m_random.ints(min, max).iterator();
        while (it.hasNext()) {
            try (final ServerSocket socket = new ServerSocket(it.next())) {
                return socket.getLocalPort();
            } catch (Throwable e) {}
        }
        throw new IllegalStateException("Can't find an available network port");
    }

    /**
     * Spawns the OpenNMS container, linked to PostgreSQL.
     */
    private void spawnOpenNMS() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.OPENNMS;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        final Path overlayRoot = initializeOverlayRoot(alias);

        final Path opennmsOverlay = overlayRoot.resolve("opennms-overlay");
        final Path opennmsLogs = overlayRoot.resolve("opennms-logs");
        final Path opennmsKarafLogs = overlayRoot.resolve("opennms-karaf-logs");

        Files.createDirectories(opennmsOverlay);
        Files.createDirectories(opennmsLogs);
        Files.createDirectories(opennmsKarafLogs);

        if (this.overlayDirectory != null) {
            Files.find(this.overlayDirectory, 10, (path, attr) -> {
                return path.toFile().isFile();
            }).forEach(path -> {
                final Path relative = Paths.get(this.overlayDirectory.toFile().toURI().relativize(path.toFile().toURI()).getPath());
                final Path to = Paths.get(opennmsOverlay.toString(), relative.toString());
                LOG.debug("Copying {} to {}", path.toAbsolutePath(), to.toAbsolutePath());
                try {
                    Files.createDirectories(to.getParent());
                    Files.copy(path.toAbsolutePath(), to.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        final List<String> binds = new ArrayList<>();
        binds.add(opennmsOverlay.toString() + ":/opennms-docker-overlay");
        binds.add(opennmsLogs.toString() + ":/var/log/opennms");
        binds.add(opennmsKarafLogs.toString() + ":/opt/opennms/data/log");

        final List<String> links = new ArrayList<>();
        links.add(String.format("%s:postgres", containerInfoByAlias.get(ContainerAlias.POSTGRES).name()));

        // Link to the Elasticsearch container, if enabled
        if (isEnabled(ContainerAlias.ELASTICSEARCH_2)) {
            links.add(String.format("%s:elasticsearch", containerInfoByAlias.get(ContainerAlias.ELASTICSEARCH_2).name()));
        } else if (isEnabled(ContainerAlias.ELASTICSEARCH_5)) {
            links.add(String.format("%s:elasticsearch", containerInfoByAlias.get(ContainerAlias.ELASTICSEARCH_5).name()));
        } else if (isEnabled(ContainerAlias.ELASTICSEARCH_6)) {
            links.add(String.format("%s:elasticsearch", containerInfoByAlias.get(ContainerAlias.ELASTICSEARCH_6).name()));
        }

        if (isEnabled(ContainerAlias.KAFKA)) {
            links.add(String.format("%s:kafka", containerInfoByAlias.get(ContainerAlias.KAFKA).name()));
        }

        Builder builder = HostConfig.builder()
                .privileged(true)
                .publishAllPorts(true)
                .links(links)
                .binds(binds);

        spawnContainer(alias, builder, Collections.emptyList());
    }

    /**
     * Spawns the Net-SNMP container.
     */
    private void spawnSnmpd() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.SNMPD;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        spawnContainer(alias, HostConfig.builder(), Collections.emptyList());
    }

    /**
     * Spawns the Sentinel container.
     */
    private void spawnSentinel() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.SENTINEL;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        final Path overlayRoot = initializeOverlayRoot(alias);

        final Path sentinelOverlay = overlayRoot.resolve("sentinel-overlay");
        final Path sentinelKarafLogs = overlayRoot.resolve("sentinel-karaf-logs");

        Files.createDirectories(sentinelOverlay.resolve("etc"));
        Files.createDirectories(sentinelKarafLogs);

        try (final FileWriter fw = new FileWriter(sentinelOverlay.resolve("etc/clean.disabled").toFile())) {
            fw.write("true\n".toCharArray());
        }

        if (this.sentinelOverlayDirectory != null) {
            Files.find(this.sentinelOverlayDirectory, 10, (path, attr) -> {
                return path.toFile().isFile();
            }).forEach(path -> {
                final Path relative = Paths.get(this.sentinelOverlayDirectory.toFile().toURI().relativize(path.toFile().toURI()).getPath());
                final Path to = Paths.get(sentinelOverlay.toString(), relative.toString());
                LOG.debug("Copying {} to {}", path.toAbsolutePath(), to.toAbsolutePath());
                try {
                    Files.createDirectories(to.getParent());
                    Files.deleteIfExists(to.toAbsolutePath());
                    Files.copy(path.toAbsolutePath(), to.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        final List<String> binds = new ArrayList<>();
        binds.add(sentinelOverlay.toString() + ":/sentinel-docker-overlay");
        binds.add(sentinelKarafLogs.toString() + ":/opt/sentinel/data/log");

        final List<String> links = Lists.newArrayList();
        links.add(String.format("%s:postgres", containerInfoByAlias.get(ContainerAlias.POSTGRES).name()));
        links.add(String.format("%s:opennms", containerInfoByAlias.get(ContainerAlias.OPENNMS).name()));

        if (isEnabled(ContainerAlias.ELASTICSEARCH_2)) {
            links.add(String.format("%s:elasticsearch", containerInfoByAlias.get(ContainerAlias.ELASTICSEARCH_2).name()));
        } else if (isEnabled(ContainerAlias.ELASTICSEARCH_5)) {
            links.add(String.format("%s:elasticsearch", containerInfoByAlias.get(ContainerAlias.ELASTICSEARCH_5).name()));
        } else if (isEnabled(ContainerAlias.ELASTICSEARCH_6)) {
            links.add(String.format("%s:elasticsearch", containerInfoByAlias.get(ContainerAlias.ELASTICSEARCH_6).name()));
        }

        if (isEnabled(ContainerAlias.KAFKA)) {
            links.add(String.format("%s:kafka", containerInfoByAlias.get(ContainerAlias.KAFKA).name()));
        }

        final Builder builder = HostConfig.builder()
                .publishAllPorts(true)
                .links(links)
                .binds(binds);

        spawnContainer(alias, builder, Collections.emptyList());
    }

    /**
     * Spawns the Tomcat container.
     */
    private void spawnTomcat() throws DockerException, InterruptedException, IOException {
        final ContainerAlias alias = ContainerAlias.TOMCAT;
        if (!(isEnabled(alias) && isSpawned(alias))) {
            return;
        }

        spawnContainer(alias, HostConfig.builder(), Collections.emptyList());
    }

    /**
     * Spawns the Minion container, linked to OpenNMS, Net-SNMP and Tomcat.
     */
    private void spawnMinions() throws DockerException, InterruptedException, IOException {
        for (final ContainerAlias alias : Arrays.asList(ContainerAlias.MINION, ContainerAlias.MINION_SAME_LOCATION, ContainerAlias.MINION_OTHER_LOCATION)) {
            if (!(isEnabled(alias) && isSpawned(alias))) {
                continue;
            }

            final Path overlayRoot = initializeOverlayRoot(alias);

            final Path minionOverlay = overlayRoot.resolve("minion-overlay");
            final Path minionKarafLogs = overlayRoot.resolve("minion-karaf-logs");

            Files.createDirectories(minionOverlay.resolve("etc"));
            Files.createDirectories(minionKarafLogs);

            try (final FileWriter fw = new FileWriter(minionOverlay.resolve("etc/clean.disabled").toFile())) {
                fw.write("true\n".toCharArray());
            }

            if (this.minionOverlayDirectory != null) {
                Files.find(this.minionOverlayDirectory, 10, (path, attr) -> {
                    return path.toFile().isFile();
                }).forEach(path -> {
                    final Path relative = Paths.get(this.minionOverlayDirectory.toFile().toURI().relativize(path.toFile().toURI()).getPath());
                    final Path to = Paths.get(minionOverlay.toString(), relative.toString());
                    LOG.debug("Copying {} to {}", path.toAbsolutePath(), to.toAbsolutePath());
                    try {
                        Files.createDirectories(to.getParent());
                        Files.deleteIfExists(to.toAbsolutePath());
                        Files.copy(path.toAbsolutePath(), to.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            final List<String> binds = new ArrayList<>();
            binds.add(minionOverlay.toString() + ":/minion-docker-overlay");
            binds.add(minionKarafLogs.toString() + ":/opt/minion/data/log");

            final List<String> links = Lists.newArrayList();
            if (isEnabled(ContainerAlias.OPENNMS)) {
                links.add(String.format("%s:opennms", containerInfoByAlias.get(ContainerAlias.OPENNMS).name()));
            }
            if (isEnabled(ContainerAlias.SNMPD)) {
                links.add(String.format("%s:snmpd", containerInfoByAlias.get(ContainerAlias.SNMPD).name()));
            }
            if (isEnabled(ContainerAlias.TOMCAT)) {
                links.add(String.format("%s:tomcat", containerInfoByAlias.get(ContainerAlias.TOMCAT).name()));
            }
            if (isEnabled(ContainerAlias.KAFKA)) {
                links.add(String.format("%s:kafka", containerInfoByAlias.get(ContainerAlias.KAFKA).name()));
            }

            final Builder builder = HostConfig.builder()
                    .publishAllPorts(true)
                    .links(links)
                    .binds(binds);

            final List<String> env = Arrays.asList(
                                                   "MINION_LOCATION=" + MINION_LOCATIONS.get(alias),
                                                   "MINION_ID=" + MINION_IDS.get(alias)
                    );
            spawnContainer(alias, builder, env);
        }
    }

    private Path initializeOverlayRoot(final ContainerAlias alias) {
        final Path overlayRoot = Paths.get("target", "overlays", getName(), alias.toString()).toAbsolutePath();

        if (!isInitialized(alias)) {
            FileUtils.removeDir(overlayRoot.toFile());
        }

        INITIALIZED_OVERLAYS.put(alias, true);
        return overlayRoot;
    }

    private boolean isInitialized(final ContainerAlias alias) {
        return INITIALIZED_OVERLAYS.containsKey(alias) && INITIALIZED_OVERLAYS.get(alias);
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
        spawnContainer(alias, hostConfigBuilder, Collections.emptyList());
    }

    /**
     * Spawns a container.
     */
    private void spawnContainer(final ContainerAlias alias, final Builder hostConfigBuilder, final List<String> env) throws DockerException, InterruptedException, IOException {
        final HostConfig hostConfig = hostConfigBuilder.build();
        final ContainerConfig containerConfig = ContainerConfig.builder()
                .image(IMAGES_BY_ALIAS.get(alias))
                .hostConfig(hostConfig)
                .hostname(getName() + ".local")
                .env(env)
                .exposedPorts(hostConfig.portBindings() != null ? hostConfig.portBindings().keySet() : Collections.emptySet())
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

        if (hostConfig.portBindings() != null) {
            final Set<Integer> containerPorts = Sets.newConcurrentHashSet();
            hostConfig.portBindings().keySet().forEach(pb -> {
                containerPorts.add(Integer.valueOf(pb));
            });
            ports.put(alias, containerPorts);
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
            @Override public String call() throws Exception {
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
        LOG.info("Waiting for OpenNMS REST service @ {}.", httpAddr);
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
        LOG.info("Waiting for OpenNMS SSH service @ {}.", sshAddr);
        LOG.info("************************************************************");
        await().atMost(2, MINUTES).pollInterval(5, SECONDS).until(SshClient.canConnectViaSsh(sshAddr, "admin", "admin"));
        await().atMost(5, MINUTES).pollInterval(5, SECONDS).until(() -> listFeatures(sshAddr, false));
        LOG.info("************************************************************");
        LOG.info("OpenNMS's Karaf Shell is online.");
        LOG.info("************************************************************");

        /*
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "5000");
        final Callable<Boolean> getJmxConnection = new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                return null;
            }
        };
        await().atMost(5, MINUTES).pollInterval(10, SECONDS).until(getJmxConnection, is(notNullValue()));
         */
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
    private void waitForSentinel() throws Exception {
        final ContainerAlias alias = ContainerAlias.SENTINEL;
        if (!isEnabled(alias)) {
            return;
        }

        final InetSocketAddress sshAddr = getServiceAddress(alias, 8301);
        LOG.info("************************************************************");
        LOG.info("Waiting for Sentinel @ {} to start.", sshAddr);
        LOG.info("************************************************************");
        await().atMost(5, MINUTES).pollInterval(5, SECONDS).until(() -> listFeatures(sshAddr, true));
    }

    /**
     * Blocks until the Karaf Shell service is available.
     */
    private void waitForMinions() throws Exception {
        for (final ContainerAlias alias : Arrays.asList(ContainerAlias.MINION, ContainerAlias.MINION_SAME_LOCATION, ContainerAlias.MINION_OTHER_LOCATION)) {
            if (!isEnabled(alias)) {
                return;
            }

            final InetSocketAddress sshAddr = getServiceAddress(alias, 8201);
            LOG.info("************************************************************");
            LOG.info("Waiting for Minion @ {} to establish connectivity with OpenNMS instance.", sshAddr);
            LOG.info("************************************************************");
            await().atMost(5, MINUTES).pollInterval(5, SECONDS).until(() -> canMinionConnectToOpenNMS(sshAddr));
            await().atMost(5, MINUTES).pollInterval(5, SECONDS).until(() -> listFeatures(sshAddr, true));
        }
    }

    public boolean canMinionConnectToOpenNMS(InetSocketAddress sshAddr) {
        try (final SshClient sshClient = new SshClient(sshAddr, "admin", "admin")) {
            // Issue the 'minion:ping' command
            PrintStream pipe = sshClient.openShell();
            pipe.println("minion:ping");
            pipe.println("logout");

            await().atMost(2, MINUTES).until(sshClient.isShellClosedCallable());

            // Grab the output
            String shellOutput = sshClient.getStdout();
            LOG.info("minion:ping output: {}", shellOutput);

            // We're expecting output of the form
            // admin@minion> minion:ping
            // Connecting to ReST...
            // OK
            // Connecting to Broker...
            // OK
            //
            // So it is sufficient to check for 2 'OK's
            return StringUtils.countMatches(shellOutput, "OK") >= 2;
        } catch (Exception e) {
            LOG.error("Failed to reach the Minion from OpenNMS.", e);
        }
        return false;
    }

    private static boolean listFeatures(final InetSocketAddress sshAddr, final boolean karaf4) {
        try (final SshClient sshClient = new SshClient(sshAddr, "admin", "admin")) {
            final PrintStream pipe = sshClient.openShell();
            if (karaf4) {
                pipe.println("feature:list -i");
            } else {
                pipe.println("features:list -i");
            }
            pipe.println("list");
            pipe.println("logout");
            try {
                await().atMost(2, MINUTES).until(sshClient.isShellClosedCallable());
                // exit listFeatures() on success
                return true;
            } finally {
                LOG.info("Features installed:\n{}", sshClient.getStdout());
            }
        } catch (final Exception e) {
            LOG.error("Failed to list features.", e);
        }
        return false;
    }

    @Override
    public DockerClient getDockerClient() {
        return docker;
    }
}
