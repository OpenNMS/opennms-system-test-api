#!/bin/bash -ex
SENTINEL_HOME=/opt/sentinel

if [ ! -e "$JAVA_HOME" ]; then
	for DIR in /usr/lib/jvm/jre /usr/lib/jvm/java; do
		if [ -e "$DIR" ] && [ -x "$DIR/bin/java" ]; then
			export JAVA_HOME="$DIR"
		fi
	done
fi
echo "JAVA HOME: ${JAVA_HOME}"
echo "JAVA VERSION: $("$JAVA_HOME/bin/java" -version)"

echo "SENTINEL HOME: ${SENTINEL_HOME}"

# Expose the Karaf shell
sed -i "/^sshHost/s/=.*/= 0.0.0.0/" ${SENTINEL_HOME}/etc/org.apache.karaf.shell.cfg

# Expose the RMI registry and server
sed -i "/^rmiRegistryHost/s/=.*/= 0.0.0.0/" ${SENTINEL_HOME}/etc/org.apache.karaf.management.cfg
sed -i "/^rmiServerHost/s/=.*/= 0.0.0.0/" ${SENTINEL_HOME}/etc/org.apache.karaf.management.cfg

# Configure basic connection to opennms
echo "http-url = http://${OPENNMS_PORT_8980_TCP_ADDR}:${OPENNMS_PORT_8980_TCP_PORT}/opennms" >> ${SENTINEL_HOME}/etc/org.opennms.sentinel.controller.cfg
echo "broker-url = failover:tcp://${OPENNMS_PORT_61616_TCP_ADDR}:${OPENNMS_PORT_61616_TCP_PORT}" >> ${SENTINEL_HOME}/etc/org.opennms.sentinel.controller.cfg

# Configure Apache Kafka as consumer
cat > ${SENTINEL_HOME}/etc/org.opennms.core.ipc.sink.kafka.consumer.cfg <<EOF
bootstrap.servers=${KAFKA_PORT_9092_TCP_ADDR}:${KAFKA_PORT_9092_TCP_PORT}
EOF

# Point PostgreSQL to the linked container
cat > ${SENTINEL_HOME}/etc/org.opennms.netmgt.distributed.datasource.cfg <<EOF
datasource.url=jdbc:postgresql://${POSTGRES_PORT_5432_TCP_ADDR}:${POSTGRES_PORT_5432_TCP_PORT}/opennms
datasource.username=${POSTGRES_USER:=postgres}
datasource.password=${POSTGRES_PASSWORD}
EOF

if [ -d /sentinel-docker-overlay/ ]; then
	echo "Overlaying files from /sentinel-docker-overlay/ onto ${SENTINEL_HOME}"
	find /sentinel-docker-overlay -ls
	rsync -ar /sentinel-docker-overlay/ "${SENTINEL_HOME}"/
fi

# Ensure everything is owned by the sentinel user
chown -R sentinel:sentinel "${SENTINEL_HOME}"

find "${SENTINEL_HOME}/data" -type d | grep -vE "(data|data/log)$" | sort -ur | while read DIR; do
	rm -rf "$DIR"
done
rm -rf "${SENTINEL_HOME}/data/log"/*

if [ -e "${SENTINEL_HOME}/etc/clean.disabled" ]; then
	$SENTINEL_HOME/bin/karaf server
else
	$SENTINEL_HOME/bin/karaf clean server
fi
