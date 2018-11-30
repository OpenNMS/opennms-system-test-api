FROM centos:7
MAINTAINER jesse@opennms.org

# Import the OpenNMS GPG key
RUN rpm --import https://yum.opennms.org/OPENNMS-GPG-KEY

COPY clean-yum.sh run-yum.sh /usr/local/bin/

# Make sure we have the EPEL repository available
RUN /usr/local/bin/run-yum.sh install -y epel-release deltarpm http://yum.opennms.org/repofiles/opennms-repo-develop-rhel7.noarch.rpm

RUN /usr/local/bin/run-yum.sh -y install  \
	ed \
	java-1.7.0-openjdk-devel \
	http://mirror.internal.opennms.com/yum/develop/common/jdk/x64/jdk-8u144-linux-x64.rpm \
	openssh-clients \
	sudo \
	unzip \
	wget \
	which \
	rsync && \
	/usr/local/bin/clean-yum.sh
