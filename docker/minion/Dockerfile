FROM stests/base
MAINTAINER jesse@opennms.org

ADD /rpms/*.rpm /
RUN ls -la /*.rpm && \
	yum install -y /*.rpm && \
	rm -rf /*.rpm && \
	yum -y clean all

COPY etc     /opt/minion/etc
COPY scripts /opt/minion/bin

RUN mkdir -p /opt/minion/data/log

# Ports
# 162/1162 - SNMP traps
# 1299     - RMI Registry
# 514/1514 - Syslog
# 8201     - Karaf SSH
# 45444    - RMI Server
EXPOSE 162/udp 1162/udp 1299 514/udp 1514/udp 5150 8201 45444

WORKDIR /opt/minion
ENV JAVA_HOME /usr/java/latest
CMD ["/opt/minion/bin/bootstrap.sh"]
