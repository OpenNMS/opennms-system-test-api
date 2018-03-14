FROM stests/opennms-base
MAINTAINER ranger@opennms.org

# Install OpenNMS
ADD /rpms/*.rpm /
RUN ls -la /*.rpm && \
	yum install -y /*.rpm && \
	rm -rf /*.rpm && \
	yum -y clean all
#	wget -qO /opt/opennms/jetty-webapps/hawtio.war https://oss.sonatype.org/content/repositories/public/io/hawt/hawtio-default/1.4.63/hawtio-default-1.4.63.war && \
#	unzip /opt/opennms/jetty-webapps/hawtio.war -d /opt/opennms/jetty-webapps/hawtio && rm -f /opt/opennms/jetty-webapps/hawtio.war

COPY etc/opennms.conf /opt/opennms/etc/opennms.conf
COPY scripts /opt/opennms/bin
 
RUN mkdir -p /opt/opennms/data/log

# Ports
# 1099 - RMI
# 5817 - Eventd XML
# 8101 - Karaf SSH
# 8980 - OpenNMS WebUI
# 18980 - JMX
# 61616 - ActiveMQ
# 50000 - Telemetry
# 50001 - Telemetry
# 50002 - Telemetry
EXPOSE 1099 5817 8101 8980 18980 50000/udp 50001/udp 50002/udp 50003/udp 61616

WORKDIR /opt/opennms
ENV JAVA_HOME /usr/java/latest
CMD ["/opt/opennms/bin/bootstrap.sh"]
