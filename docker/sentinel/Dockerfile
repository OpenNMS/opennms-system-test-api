FROM stests/base
MAINTAINER ranger@opennms.org

ADD /rpms/*.rpm /
RUN ls -la /*.rpm && \
	/usr/local/bin/run-yum.sh install -y /*.rpm && \
	rm -rf /*.rpm && \
	/usr/local/bin/clean-yum.sh

#COPY etc     /opt/sentinel/etc
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
