package org.opennms.test.system.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.cxf.helpers.IOUtils;
import org.opennms.test.system.api.NewTestEnvironment.ContainerAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestEnvironmentBuilder {

    public static class EnvironmentBuilder {
        private static final String TEMP_DIR_PREFIX = "container-overlay";

        private Path m_overlay;

        public EnvironmentBuilder addFiles(final Path directory, final String targetDirectory) {
            if (directory == null || !directory.toFile().isDirectory()) {
                throw new RuntimeException("You must specify a source directory!");
            }

            final Path root = directory.toAbsolutePath();
            try (final Stream<Path> paths = Files.walk(root)) {
                paths.forEach(from -> {
                    if (from.toFile().isDirectory()) {
                        return;
                    }
                    System.err.println("from="+from);
                    final Path to = Paths.get(targetDirectory).resolve(root.relativize(from));
                    System.err.println("to="+to);
                    //System.err.println(root.relativize(from));
                    try {
                        addFile(from.toFile().toURI().toURL(), to.toString());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to copy " + from + " to " + to);
                    }
                });
            } catch (final Exception e) {
                throw new RuntimeException("failed to walk directory " + root, e);
            }

            return this;
        }

        public EnvironmentBuilder addFile(final String contents, final String target) {
            final File targetFile = createFile(target);
            try (final Writer w = new FileWriter(targetFile)) {
                w.write(contents);
            } catch (final IOException e) {
                throw new RuntimeException("Failed to create "+target+" file in $OPENNMS_HOME!", e);
            }
            return this;
        }

        public EnvironmentBuilder addFile(final URL source, final String target) {
            final File targetFile = createFile(target);
            try (final InputStream is = source.openStream(); final OutputStream os = new FileOutputStream(targetFile)) {
                IOUtils.copy(is, os);
                return this;
            } catch (final IOException e) {
                throw new RuntimeException("Failed to copy " + source + " to $OPENNMS_HOME/" + target, e);
            }
        }

        public Path build() {
            return m_overlay;
        }

        protected File createFile(final String path) {
            if (m_overlay == null) {
                try {
                    m_overlay = Files.createTempDirectory(TEMP_DIR_PREFIX).toAbsolutePath();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create " + TEMP_DIR_PREFIX + " temporary directory!");
                }
            }

            final Path filePath = m_overlay.resolve(path);
            filePath.getParent().toFile().mkdirs();
            final File file = filePath.toFile();
            return file;
        }
    }

    public static class OpenNMSEnvironmentBuilder extends EnvironmentBuilder {

        private boolean m_optIn = false; // opt-out by default; user can specify explicitly by calling .optIn(boolean)

        public OpenNMSEnvironmentBuilder optIn(final boolean optIn) {
            m_optIn = optIn;
            return this;
        }

        @Override
        public OpenNMSEnvironmentBuilder addFiles(final Path directory, final String targetDirectory) {
            super.addFiles(directory, targetDirectory);
            return this;
        }

        @Override
        public OpenNMSEnvironmentBuilder addFile(final String contents, final String target) {
            super.addFile(contents, target);
            return this;
        }

        @Override
        public OpenNMSEnvironmentBuilder addFile(final URL source, final String target) {
            super.addFile(source, target);
            return this;
        }

        @Override
        public Path build() {
            final File datachoices = createFile("etc/org.opennms.features.datachoices.cfg");
            try (final Writer w = new FileWriter(datachoices)) {
                w.write("enabled=" + m_optIn + "\n" +
                        "acknowledged-by=admin\n" +
                        "acknowledged-at=Thu Mar 24 10\\:41\\:25 EDT 2016\n");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to create opt-in enabled="+m_optIn+" file in $OPENNMS_HOME/etc!", e);
            }

            return super.build();
        }

    }

    private static Logger LOG = LoggerFactory.getLogger(TestEnvironmentBuilder.class);

    private String m_name = null;

    EnumMap<TestEnvironmentProperty,Object> properties = new EnumMap<>(TestEnvironmentProperty.class);

    private Set<ContainerAlias> m_containers = new LinkedHashSet<>();

    private OpenNMSEnvironmentBuilder m_opennmsEnvironmentBuilder;

    private EnvironmentBuilder m_minionEnvironmentBuilder;

    public TestEnvironmentBuilder() {
    }

    public TestEnvironmentBuilder skipTearDown(boolean skipTearDown) {
        properties.put(TestEnvironmentProperty.SKIP_TEAR_DOWN, skipTearDown);
        return this;
    }

    public TestEnvironmentBuilder useExisting(boolean useExisting) {
        properties.put(TestEnvironmentProperty.USE_EXISTING, useExisting);
        return this;
    }

    public TestEnvironmentBuilder all() {
        opennms();
        minion();
        snmpd();
        tomcat();

        return this;
    }

    public TestEnvironmentBuilder allWithAdditionalMinions() {
        return all().minionWithSameLocation().minionWithOtherLocation();
    }

    public TestEnvironmentBuilder postgres() {
        if (m_containers.contains(ContainerAlias.POSTGRES)) {
            return this;
        }

        m_containers.add(ContainerAlias.POSTGRES);
        return this;
    }

    public TestEnvironmentBuilder kafka() {
        if (m_containers.contains(ContainerAlias.KAFKA)) {
            return this;
        }

        m_containers.add(ContainerAlias.KAFKA);
        return this;
    }

    public TestEnvironmentBuilder es2() {
        if (m_containers.contains(ContainerAlias.ELASTICSEARCH_2)) {
            return this;
        }

        m_containers.add(ContainerAlias.ELASTICSEARCH_2);
        return this;
    }

    public TestEnvironmentBuilder es5() {
        if (m_containers.contains(ContainerAlias.ELASTICSEARCH_5)) {
            return this;
        }

        m_containers.add(ContainerAlias.ELASTICSEARCH_5);
        return this;
    }

    public TestEnvironmentBuilder es6() {
        if (m_containers.contains(ContainerAlias.ELASTICSEARCH_6)) {
            return this;
        }

        m_containers.add(ContainerAlias.ELASTICSEARCH_6);
        return this;
    }

    public TestEnvironmentBuilder opennms() {
        if (m_containers.contains(ContainerAlias.OPENNMS)) {
            return this;
        }
        postgres();
        m_containers.add(ContainerAlias.OPENNMS);

        return this;
    }

    public TestEnvironmentBuilder minion() {
        if (m_containers.contains(ContainerAlias.MINION)) {
            return this;
        }
        m_containers.add(ContainerAlias.MINION);
        return this;
    }
    public TestEnvironmentBuilder minionWithSameLocation() {
        if (m_containers.contains(ContainerAlias.MINION_SAME_LOCATION)) {
            return this;
        }
        m_containers.add(ContainerAlias.MINION_SAME_LOCATION);
        return this;
    }
    public TestEnvironmentBuilder minionWithOtherLocation() {
        if (m_containers.contains(ContainerAlias.MINION_OTHER_LOCATION)) {
            return this;
        }
        m_containers.add(ContainerAlias.MINION_OTHER_LOCATION);
        return this;
    }

    public TestEnvironmentBuilder snmpd() {
        if (m_containers.contains(ContainerAlias.SNMPD)) {
            return this;
        }
        m_containers.add(ContainerAlias.SNMPD);
        return this;
    }

    public TestEnvironmentBuilder tomcat() {
        m_containers.add(ContainerAlias.TOMCAT);
        return this;
    }

    public TestEnvironmentBuilder name(final String name) {
        m_name = name;
        return this;
    }

    public OpenNMSEnvironmentBuilder withOpenNMSEnvironment() {
        if (m_opennmsEnvironmentBuilder == null) {
            m_opennmsEnvironmentBuilder = new OpenNMSEnvironmentBuilder();
        }
        return m_opennmsEnvironmentBuilder;
    }

    public EnvironmentBuilder withMinionEnvironment() {
        if (m_minionEnvironmentBuilder == null) {
            m_minionEnvironmentBuilder = new EnvironmentBuilder();
        }
        return m_minionEnvironmentBuilder;
    }

    public TestEnvironment build() {
        if (m_containers.size() == 0) {
            all();
        }

        LOG.debug("Creating environment with containers: {}", m_containers);
        if ((Boolean)properties.get(TestEnvironmentProperty.USE_EXISTING)) {
            return new ExistingTestEnvironment();
        } else {
            final Path opennmsOverlay = (m_opennmsEnvironmentBuilder == null ? null : m_opennmsEnvironmentBuilder.build());
            final Path minionOverlay = (m_minionEnvironmentBuilder == null ? null : m_minionEnvironmentBuilder.build());

            return new NewTestEnvironment(m_name, properties, opennmsOverlay, minionOverlay, m_containers);
        }
    }
}
