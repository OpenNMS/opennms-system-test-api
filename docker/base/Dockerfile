FROM centos:7
MAINTAINER jesse@opennms.org

# Import the OpenNMS GPG key
RUN rpm --import https://yum.opennms.org/OPENNMS-GPG-KEY

# Make sure we have the EPEL repository available
RUN yum install -y epel-release deltarpm http://yum.opennms.org/repofiles/opennms-repo-develop-rhel7.noarch.rpm

RUN yum -y install  \
	ed \
	java-1.7.0-openjdk-devel \
	http://mirror.internal.opennms.com/yum/develop/common/jdk/x64/jdk-8u181-linux-x64.rpm \
	openssh-clients \
	sudo \
	unzip \
	wget \
	which \
	net-tools \
	rsync && \
	yum -y clean all && \
	rm -rf /var/cache/yum
