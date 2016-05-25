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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.cxf.helpers.FileUtils;
import org.apache.cxf.helpers.IOUtils;
import org.opennms.test.system.api.NewTestEnvironment.ContainerAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestEnvironmentBuilder {
    private static Logger LOG = LoggerFactory.getLogger(TestEnvironmentBuilder.class);

    private static AtomicInteger m_containerNumber = new AtomicInteger(1);
    private boolean m_skipTearDown = false;
    private boolean m_useExisting = false;
    private Map<ContainerAlias,List<String>> m_binds = new HashMap<>();
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

    public TestEnvironmentBuilder all() throws IOException {
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

    public TestEnvironmentBuilder opennms() throws IOException {
        if (m_containers.contains(ContainerAlias.OPENNMS)) {
            return this;
        }
        postgres();
        m_containers.add(ContainerAlias.OPENNMS);

        final Path overlayRoot = Paths.get("target", "overlays", Integer.toString(m_containerNumber.get())).toAbsolutePath();
        if (overlayRoot.toFile().exists()) {
            FileUtils.removeDir(overlayRoot.toFile());
        }

        m_opennmsOverlay = overlayRoot.resolve("opennms-overlay");
        final Path opennmsLogs = overlayRoot.resolve("opennms-logs");
        final Path opennmsKarafLogs = overlayRoot.resolve("opennms-karaf-logs");

        final List<String> binds = new ArrayList<>();
        binds.add(m_opennmsOverlay.toString() + ":/opennms-docker-overlay");
        binds.add(opennmsLogs.toString() + ":/var/log/opennms");
        binds.add(opennmsKarafLogs.toString() + ":/opt/opennms/data/log");

        m_binds.put(ContainerAlias.OPENNMS, binds);
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
        final Path filePath = m_opennmsOverlay.resolve(path);
        filePath.getParent().toFile().mkdirs();
        final File file = filePath.toFile();
        return file;
    }

    public TestEnvironment build() throws IOException {
        if (m_containers.size() == 0) {
            all();
        }

        m_containerNumber.incrementAndGet();

        LOG.debug("Creating environment with containers: {}", m_containers);
        if (m_useExisting) {
            return new ExistingTestEnvironment();
        } else {
            return new NewTestEnvironment(m_skipTearDown, m_binds, m_containers);
        }
    }
}
