FROM centos:7
MAINTAINER jesse@opennms.org

# Keep this line in sync with the opennms image so that they can share a common base image
RUN yum install -y openssh-clients java-1.8.0-openjdk-devel which wget unzip ed

ADD /rpms/*.rpm /
RUN yum localinstall -y /*.rpm && rm -rf /*.rpm

COPY etc     /opt/minion/etc
COPY scripts /opt/minion/bin

# Ports
# 162   - SNMP traps
# 1299  - RMI Registry
# 1514  - Syslog
# 8201  - Karaf SSH
# 45444 - RMI Server
EXPOSE 162/udp 1299 1514/udp 5150 8201 45444

WORKDIR /opt/minion
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk
CMD ["/opt/minion/bin/bootstrap.sh"]
