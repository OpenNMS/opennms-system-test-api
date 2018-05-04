#!/bin/bash -ex
SENTINEL_HOME=/opt/sentinel

echo "SENTINEL HOME: ${SENTINEL_HOME}"

# Expose the Karaf shell
sed -i s/sshHost.*/sshHost=0.0.0.0/g "${SENTINEL_HOME}/etc/org.apache.karaf.shell.cfg"

# Expose the RMI registry and server
sed -i s/rmiRegistryHost.*/rmiRegistryHost=0.0.0.0/g "${SENTINEL_HOME}/etc/org.apache.karaf.management.cfg"
sed -i s/rmiServerHost.*/rmiServerHost=0.0.0.0/g "${SENTINEL_HOME}/etc/org.apache.karaf.management.cfg"

# Point the Apache Kafka sink to the linked container
cat > ${SENTINEL_HOME}/etc/org.opennms.core.ipc.sink.kafka.cfg <<EOF
bootstrap.servers=${KAFKA_PORT_9092_TCP_ADDR}:${KAFKA_PORT_9092_TCP_PORT}
acks=1
EOF

echo "Overlaying files from /sentinel-docker-overlay/ onto ${SENTINEL_HOME}"
find /sentinel-docker-overlay -ls
rsync -ar /sentinel-docker-overlay/ "${SENTINEL_HOME}"/

find "${SENTINEL_HOME}/data" -type d | grep -vE "(data|data/log)$" | sort -ur | while read DIR; do
	rm -rf "$DIR"
done
rm -rf "${SENTINEL_HOME}/data/log"/*

if [ -e "${SENTINEL_HOME}/etc/clean.disabled" ]; then
	$SENTINEL_HOME/bin/karaf server
else
	$SENTINEL_HOME/bin/karaf clean server
fi
