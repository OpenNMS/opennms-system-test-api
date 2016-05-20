FROM centos:7
MAINTAINER jesse@opennms.org

RUN yum install -y net-snmp && yum clean all

COPY etc /etc
COPY scripts/bootstrap.sh /

ENV LOCATION docker

EXPOSE 161/udp

ENTRYPOINT ["/bootstrap.sh"]
