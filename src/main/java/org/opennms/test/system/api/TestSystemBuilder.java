package org.opennms.test.system.api;

public class TestSystemBuilder {

    private boolean m_skipTearDown = false;
    private boolean m_useExisting = false;

    public TestSystemBuilder skipTearDown(boolean skipTearDown) {
        m_skipTearDown = skipTearDown;
        return this;
    }

    public TestSystemBuilder useExisting(boolean useExisting) {
        m_useExisting = useExisting;
        return this;
    }

    public TestSystem build() {
        if (m_useExisting) {
            return new ExistingTestSystem();
        } else {
            return new NewTestSystem(m_skipTearDown);
        }
    }
}
