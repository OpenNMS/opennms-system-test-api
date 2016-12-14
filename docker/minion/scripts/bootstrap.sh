#!/bin/bash -ex
MINION_HOME=/opt/minion

echo "MINION HOME: ${MINION_HOME}"

# Expose the Karaf shell
sed -i s/sshHost.*/sshHost=0.0.0.0/g "${MINION_HOME}/etc/org.apache.karaf.shell.cfg"

# Expose the RMI registry and server
sed -i s/rmiRegistryHost.*/rmiRegistryHost=0.0.0.0/g "${MINION_HOME}/etc/org.apache.karaf.management.cfg"
sed -i s/rmiServerHost.*/rmiServerHost=0.0.0.0/g "${MINION_HOME}/etc/org.apache.karaf.management.cfg"

echo "location = ${MINION_LOCATION:=MINION}" > $MINION_HOME/etc/org.opennms.minion.controller.cfg
echo "id = 00000000-0000-0000-0000-000000ddba11" >> $MINION_HOME/etc/org.opennms.minion.controller.cfg
echo "http-url = http://${OPENNMS_PORT_8980_TCP_ADDR}:${OPENNMS_PORT_8980_TCP_PORT}" >> $MINION_HOME/etc/org.opennms.minion.controller.cfg
echo "broker-url = failover:tcp://${OPENNMS_PORT_61616_TCP_ADDR}:${OPENNMS_PORT_61616_TCP_PORT}" >> $MINION_HOME/etc/org.opennms.minion.controller.cfg

echo "Overlaying files from /minion-docker-overlay/ onto ${MINION_HOME}"
find /minion-docker-overlay -ls
rsync -ar /minion-docker-overlay/ "${MINION_HOME}"/

find "${MINION_HOME}/data" -type d | grep -vE "(data|data/log)$" | sort -ur | while read DIR; do
	rm -rf "$DIR"
done
rm -rf "${MINION_HOME}/data/log"/*

if [ -e "${MINION_HOME}/etc/clean.disabled" ]; then
	$MINION_HOME/bin/karaf server
else
	$MINION_HOME/bin/karaf clean server
fi
