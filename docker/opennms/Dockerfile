FROM centos:7
MAINTAINER jesse@opennms.org

# Keep this line in sync with the minion image so that they can share a common base image
RUN yum install -y openssh-clients java-1.8.0-openjdk-devel which wget unzip ed

# OpenNMS dependencies
RUN rpm --import https://yum.opennms.org/OPENNMS-GPG-KEY && \
	wget -qO /jicmp.rpm http://yum.opennms.org/stable/rhel7/jicmp/jicmp-1.4.5-2.x86_64.rpm && \
	wget -qO /jicmp6.rpm http://yum.opennms.org/stable/rhel7/jicmp6/jicmp6-1.2.4-1.x86_64.rpm && \
	wget -qO /jrrd2.rpm http://yum.opennms.org/stable/rhel7/jrrd2/jrrd2-2.0.3-3.el7.centos.x86_64.rpm && \
	wget -qO /jrrd.rpm http://yum.opennms.org/stable/centos7/jrrd/jrrd-1.1.0-2.el7.centos.x86_64.rpm && \
    yum install -y postgresql-server

# OpenNMS & Hawtio
ADD /rpms/*.rpm /
RUN yum localinstall -y /*.rpm && rm -rf /*.rpm && \
    wget -qO /opt/opennms/jetty-webapps/hawtio.war https://oss.sonatype.org/content/repositories/public/io/hawt/hawtio-default/1.4.63/hawtio-default-1.4.63.war && \
    unzip /opt/opennms/jetty-webapps/hawtio.war -d /opt/opennms/jetty-webapps/hawtio && rm -f /opt/opennms/jetty-webapps/hawtio.war

COPY etc     /opt/opennms/etc
COPY scripts /opt/opennms/bin
 
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
