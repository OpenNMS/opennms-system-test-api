#!/bin/bash
OPENNMS_HOME=/opt/opennms

if [ ! -e "$JAVA_HOME" ]; then
	for DIR in /usr/lib/jvm/jre /usr/lib/jvm/java; do
		if [ -e "$DIR" ] && [ -x "$DIR/bin/java" ]; then
			export JAVA_HOME="$DIR"
		fi
	done
fi
echo "JAVA HOME: ${JAVA_HOME}"
echo "JAVA VERSION: $("$JAVA_HOME/bin/java" -version)"

echo "OPENNMS HOME: "${OPENNMS_HOME}

# Point PostgreSQL to the linked container
sed -i 's|url=.*opennms.*|url="jdbc:postgresql://'"${POSTGRES_PORT_5432_TCP_ADDR}:${POSTGRES_PORT_5432_TCP_PORT}/opennms"'"|g' "${OPENNMS_HOME}/etc/opennms-datasources.xml"
sed -i 's|url=.*template1.*|url="jdbc:postgresql://'"${POSTGRES_PORT_5432_TCP_ADDR}:${POSTGRES_PORT_5432_TCP_PORT}/template1"'"|g' "${OPENNMS_HOME}/etc/opennms-datasources.xml"

# Point Elasticsearch to the linked container
cat > ${OPENNMS_HOME}/etc/org.opennms.features.elasticsearch.eventforwarder.cfg <<EOF
elasticsearchIp=${ELASTICSEARCH_PORT_9200_TCP_ADDR}
elasticsearchHttpPort=${ELASTICSEARCH_PORT_9200_TCP_PORT}
elasticsearchTransportPort=${ELASTICSEARCH_PORT_9300_TCP_PORT}
EOF

# Point Elasticsearch REST to the linked container
if [ ! -e ${OPENNMS_HOME}/etc/org.opennms.plugin.elasticsearch.rest.forwarder.cfg ]; then
cat > ${OPENNMS_HOME}/etc/org.opennms.plugin.elasticsearch.rest.forwarder.cfg <<EOF
elasticsearchUrl=http://${ELASTICSEARCH_PORT_9200_TCP_ADDR}:${ELASTICSEARCH_PORT_9200_TCP_PORT}
EOF
fi

# Point the Apache Kafka sink to the linked container
mkdir -p "${OPENNMS_HOME}/etc/opennms.properties.d"
cat > ${OPENNMS_HOME}/etc/opennms.properties.d/kafka-server.properties <<EOF
org.opennms.core.ipc.sink.kafka.bootstrap.servers=${KAFKA_PORT_9092_TCP_ADDR}:${KAFKA_PORT_9092_TCP_PORT}
org.opennms.core.ipc.rpc.kafka.bootstrap.servers=${KAFKA_PORT_9092_TCP_ADDR}:${KAFKA_PORT_9092_TCP_PORT}
EOF

# Expose the Karaf shell
sed -i s/sshHost.*/sshHost=0.0.0.0/g "${OPENNMS_HOME}/etc/org.apache.karaf.shell.cfg"

# Expose ActiveMQ
grep '<transportConnector name="openwire" uri="tcp://0.0.0.0:61616' /opt/opennms/etc/opennms-activemq.xml | grep -v '<!--' >/dev/null
if [ $? != 0 ]; then
	# Search for the <transportConnectors> tag and insert the externally accessible connector after it
	echo "Editing opennms-activemq.xml..."
	ed "${OPENNMS_HOME}/etc/opennms-activemq.xml" <<EOF
/<transportConnectors>/a
<transportConnector name="openwire" uri="tcp://0.0.0.0:61616?useJmx=false&amp;maximumConnections=1000&amp;wireformat.maxFrameSize=104857600"/>
.
wq
EOF
fi

# Setup Newts
if [ ${USE_NEWTS} == "true" ]; then
    echo "org.opennms.timeseries.strategy=newts" > /opt/opennms/etc/opennms.properties.d/newts.properties
    echo "org.opennms.newts.config.hostname=cassandra" >> /opt/opennms/etc/opennms.properties.d/newts.properties
fi

#echo "Editing custom.properties..."
#ed "${OPENNMS_HOME}/etc/custom.properties" <<EOF
#/org.opennms.netmgt.snmp;/a
#org.snmp4j,\\
#.
#wq
#EOF

if [ -d /opennms-docker-overlay/ ]; then
	echo "Overlaying files from /opennms-docker-overlay/ onto ${OPENNMS_HOME}"
	find /opennms-docker-overlay -ls
	rsync -ar /opennms-docker-overlay/ "${OPENNMS_HOME}"/
fi

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

# Initialize JRE
${OPENNMS_HOME}/bin/runjava -s

# Initialize newts
if [ ${USE_NEWTS} == "true" ]; then
    ${OPENNMS_HOME}/bin/newts init
fi

# Start OpenNMS
rm -rf ${OPENNMS_HOME}/data
${OPENNMS_HOME}/bin/install -dis
"${OPENNMS_HOME}/bin/opennms" -f start
