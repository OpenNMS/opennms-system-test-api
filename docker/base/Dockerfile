FROM centos:7
MAINTAINER jesse@opennms.org

# Import the OpenNMS GPG key
RUN rpm --import https://yum.opennms.org/OPENNMS-GPG-KEY

# Make sure we have the EPEL repository available
RUN yum install -y epel-release deltarpm http://yum.opennms.org/repofiles/opennms-repo-develop-rhel7.noarch.rpm

RUN yum -y install  \
	ed \
	java-1.8.0-openjdk-devel \
	openssh-clients \
	unzip \
	wget \
	which \
	rsync && \
	yum -y clean all
