FROM stests/base
MAINTAINER jesse@opennms.org

# Install OpenNMS dependencies
RUN yum install -y \
	jicmp \
	jicmp6 \
	jrrd \
	jrrd2 \
	postgresql \
	postgresql-server \
	R && \
	yum -y clean all

# Install OpenNMS & Hawtio
ADD /rpms/*.rpm /
RUN yum install -y /*.rpm && \
	rm -rf /*.rpm && \
	yum -y clean all && \
	wget -qO /opt/opennms/jetty-webapps/hawtio.war https://oss.sonatype.org/content/repositories/public/io/hawt/hawtio-default/1.4.63/hawtio-default-1.4.63.war && \
	unzip /opt/opennms/jetty-webapps/hawtio.war -d /opt/opennms/jetty-webapps/hawtio && rm -f /opt/opennms/jetty-webapps/hawtio.war

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
EXPOSE 1099 5817 8101 8980 18980 61616

WORKDIR /opt/opennms
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk
CMD ["/opt/opennms/bin/bootstrap.sh"]
