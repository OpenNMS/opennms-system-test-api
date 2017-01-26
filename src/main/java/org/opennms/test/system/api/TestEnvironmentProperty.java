package org.opennms.test.system.api;

public enum TestEnvironmentProperty {
    /**
     * Set to true if the containers should be kept running after the tests complete
     * (regardless of whether or not they were successful).
     */
    SKIP_TEAR_DOWN,
    /**
     * If set to true, do not initialize the Docker environment and use an existing
     * OpenNMS environment instead.
     */
    USE_EXISTING,
    /**
     * Default number of partitions per topic in the Apache Kafka container.
     */
    KAFKA_PARTITIONS
}