FROM stests/base
MAINTAINER ranger@opennms.org

# Install OpenNMS dependencies
RUN /usr/local/bin/run-yum.sh install -y \
	jicmp \
	jicmp6 \
	jrrd \
	jrrd2 \
	postgresql \
	postgresql-server \
	R && \
	/usr/local/bin/clean-yum.sh
