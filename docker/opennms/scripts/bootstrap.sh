#! /bin/bash
OPENNMS_HOME=/opt/opennms

echo "OPENNMS HOME: "${OPENNMS_HOME}

# Point PostgreSQL to the linked container
sed -i 's|url=.*opennms.*|url="jdbc:postgresql://'"${POSTGRES_PORT_5432_TCP_ADDR}:${POSTGRES_PORT_5432_TCP_PORT}/opennms"'"|g' "${OPENNMS_HOME}/etc/opennms-datasources.xml"
sed -i 's|url=.*template1.*|url="jdbc:postgresql://'"${POSTGRES_PORT_5432_TCP_ADDR}:${POSTGRES_PORT_5432_TCP_PORT}/template1"'"|g' "${OPENNMS_HOME}/etc/opennms-datasources.xml"

# Expose the Karaf shell
sed -i s/sshHost.*/sshHost=0.0.0.0/g "${OPENNMS_HOME}/etc/org.apache.karaf.shell.cfg"

# Expose ActiveMQ
# Search for the <transportConnectors> tag and insert the externally accessible connector after it
echo "Editing opennms-activemq.xml..."
ed "${OPENNMS_HOME}/etc/opennms-activemq.xml" <<EOF
/<transportConnectors>/a
<transportConnector name="openwire" uri="tcp://0.0.0.0:61616?useJmx=false&amp;maximumConnections=1000&amp;wireformat.maxFrameSize=104857600"/>
.
wq
EOF

#echo "Editing custom.properties..."
#ed "${OPENNMS_HOME}/etc/custom.properties" <<EOF
#/org.opennms.netmgt.snmp;/a
#org.snmp4j,\\
#.
#wq
#EOF

echo "Overlaying files from /opennms-docker-overlay/ onto ${OPENNMS_HOME}"
find /opennms-docker-overlay -ls
rsync -ar /opennms-docker-overlay/ "${OPENNMS_HOME}"/

echo "Waiting for Postgres to start..."
WAIT=0
while ! $(timeout 1 bash -c 'cat < /dev/null > /dev/tcp/$POSTGRES_PORT_5432_TCP_ADDR/$POSTGRES_PORT_5432_TCP_PORT'); do
  sleep 1
  WAIT=$(($WAIT + 1))
  if [ "$WAIT" -gt 15 ]; then
    echo "Error: Timeout waiting for Postgres to start"
    exit 1
  fi
done

# Start OpenNMS
rm -rf ${OPENNMS_HOME}/data
${OPENNMS_HOME}/bin/runjava -s
${OPENNMS_HOME}/bin/install -dis
"${OPENNMS_HOME}/bin/opennms" -f start
