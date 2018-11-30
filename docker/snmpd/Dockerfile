FROM centos:7
MAINTAINER jesse@opennms.org

COPY clean-yum.sh run-yum.sh /usr/local/bin/
RUN /usr/local/bin/run-yum.sh install -y net-snmp && /usr/local/bin/clean-yum.sh

COPY etc /etc
COPY scripts/bootstrap.sh /

ENV LOCATION docker

EXPOSE 161/udp

ENTRYPOINT ["/bootstrap.sh"]
