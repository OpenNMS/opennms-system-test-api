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
# 1399     - RMI Registry
# 8301     - Karaf SSH
# 46444    - RMI Server
EXPOSE 1399 8301 46444

WORKDIR /opt/sentinel
ENV JAVA_HOME /usr/java/latest
CMD ["/opt/sentinel/bin/bootstrap.sh"]
