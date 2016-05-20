package org.opennms.test.system.api;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.opennms.test.system.api.NewTestEnvironment.ContainerAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestEnvironmentBuilder {
    private static Logger LOG = LoggerFactory.getLogger(TestEnvironmentBuilder.class);

    private boolean m_skipTearDown = false;
    private boolean m_useExisting = false;
    private Path m_overlayDir = null;
    private Set<ContainerAlias> m_containers = new LinkedHashSet<>();

    public TestEnvironmentBuilder() {
    }

    public TestEnvironmentBuilder skipTearDown(boolean skipTearDown) {
        m_skipTearDown = skipTearDown;
        return this;
    }

    public TestEnvironmentBuilder useExisting(boolean useExisting) {
        m_useExisting = useExisting;
        return this;
    }

    public TestEnvironmentBuilder all() {
        for (final ContainerAlias container : ContainerAlias.values()) {
            m_containers.add(container);
        }
        return this;
    }

    public TestEnvironmentBuilder opennms() {
        m_containers.add(ContainerAlias.POSTGRES);
        m_containers.add(ContainerAlias.OPENNMS);
        return this;
    }

    public TestEnvironmentBuilder minion() {
        m_containers.add(ContainerAlias.MINION);
        return this;
    }

    public TestEnvironmentBuilder snmpd() {
        m_containers.add(ContainerAlias.SNMPD);
        return this;
    }

    public TestEnvironmentBuilder tomcat() {
        m_containers.add(ContainerAlias.TOMCAT);
        return this;
    }

    public TestEnvironmentBuilder optIn(final boolean optIn) {
        final File datachoices = createFile("etc/org.opennms.features.datachoices.cfg");
        try (final Writer w = new FileWriter(datachoices)) {
            w.write("enabled=" + optIn + "\n" +
                    "acknowledged-by=admin\n" +
                    "acknowledged-at=Thu Mar 24 10\\:41\\:25 EDT 2016\n");
        } catch (final IOException e) {
            LOG.warn("Failed to create opt-in={} file in $OPENNMS_HOME/etc!", optIn);
        }
        return this;
    }

    protected File createFile(final String path) {
        if (m_overlayDir == null) {
            try {
                m_overlayDir = Files.createTempDirectory("opennms-overlay");
            } catch (IOException e) {
                throw new RuntimeException("Unable to create OpenNMS overlay temporary directory!", e);
            }
        }
        final Path file = m_overlayDir.resolve(path);
        file.getParent().toFile().mkdirs();
        return file.toFile();
    }

    public TestEnvironment build() {
        if (m_containers.size() == 0) {
            all();
        }
        
        LOG.debug("Creating environment with containers: {}", m_containers);
        if (m_useExisting) {
            return new ExistingTestEnvironment();
        } else {
            return new NewTestEnvironment(m_skipTearDown, m_overlayDir, m_containers);
        }
    }
}
