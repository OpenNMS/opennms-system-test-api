package org.opennms.test.system.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.cxf.helpers.IOUtils;
import org.opennms.test.system.api.NewTestEnvironment.ContainerAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

public class TestEnvironmentBuilder {
    private static Logger LOG = LoggerFactory.getLogger(TestEnvironmentBuilder.class);

    private String m_name = null;
    private boolean m_skipTearDown = false;
    private boolean m_useExisting = false;
    private Set<ContainerAlias> m_containers = new LinkedHashSet<>();

    private Path m_opennmsOverlay;

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
        opennms();
        minion();
        snmpd();
        tomcat();

        return this;
    }

    public TestEnvironmentBuilder postgres() {
        if (m_containers.contains(ContainerAlias.POSTGRES)) {
            return this;
        }

        m_containers.add(ContainerAlias.POSTGRES);
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

    public TestEnvironmentBuilder optIn(final boolean optIn) {
        final File datachoices = createFile("etc/org.opennms.features.datachoices.cfg");
        try (final Writer w = new FileWriter(datachoices)) {
            w.write("enabled=" + optIn + "\n" +
                    "acknowledged-by=admin\n" +
                    "acknowledged-at=Thu Mar 24 10\\:41\\:25 EDT 2016\n");
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create opt-in="+optIn+" file in $OPENNMS_HOME/etc!", e);
        }
        return this;
    }

    public TestEnvironmentBuilder addFile(final String contents, final String target) {
        final File targetFile = createFile(target);
        try (final Writer w = new FileWriter(targetFile)) {
            w.write(contents);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create "+target+" file in $OPENNMS_HOME!", e);
        }
        return this;
    }

    public TestEnvironmentBuilder addFile(final URL source, final String target) {
        final File targetFile = createFile(target);
        try (final InputStream is = source.openStream(); final OutputStream os = new FileOutputStream(targetFile)) {
            IOUtils.copy(is, os);
            return this;
        } catch (final IOException e) {
            throw new RuntimeException("Failed to copy " + source + " to $OPENNMS_HOME/" + target, e);
        }
    }

    protected File createFile(final String path) {
        if (m_opennmsOverlay == null) {
            m_opennmsOverlay = Files.createTempDir().toPath();
        }

        final Path filePath = m_opennmsOverlay.resolve(path);
        filePath.getParent().toFile().mkdirs();
        final File file = filePath.toFile();
        return file;
    }

    public TestEnvironment build() {
        if (m_containers.size() == 0) {
            all();
        }

        LOG.debug("Creating environment with containers: {}", m_containers);
        if (m_useExisting) {
            return new ExistingTestEnvironment();
        } else {
            return new NewTestEnvironment(m_name, m_skipTearDown, m_opennmsOverlay, m_containers);
        }
    }
}
