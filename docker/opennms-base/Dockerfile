FROM stests/base
MAINTAINER ranger@opennms.org

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
