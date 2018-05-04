FROM stests/base
MAINTAINER ranger@opennms.org

ADD /rpms/*.rpm /
RUN ls -la /*.rpm && \
	yum install -y /*.rpm && \
	rm -rf /*.rpm && \
	yum -y clean all

COPY etc     /opt/sentinel/etc
COPY scripts /opt/sentinel/bin

RUN mkdir -p /opt/sentinel/data/log

# Ports
# 162/1162 - SNMP traps
# 1399     - RMI Registry
# 514/1514 - Syslog
# 8301     - Karaf SSH
# 46444    - RMI Server
# 50000    - Telemetry
# 50001    - Telemetry
# 50002    - Telemetry
EXPOSE 162/udp 1162/udp 1399 514/udp 1514/udp 5150 8301 46444 50000/udp 50001/udp 50002/udp 50003/udp

WORKDIR /opt/sentinel
ENV JAVA_HOME /usr/java/latest
CMD ["/opt/sentinel/bin/bootstrap.sh"]
