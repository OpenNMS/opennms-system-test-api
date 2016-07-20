FROM centos:7
MAINTAINER jesse@opennms.org

# Import the OpenNMS GPG key
RUN rpm --import https://yum.opennms.org/OPENNMS-GPG-KEY

# bring the container up-to-date
RUN yum -y upgrade && yum -y clean all

# Make sure we have the EPEL repository available
RUN yum install -y epel-release deltarpm http://yum.opennms.org/repofiles/opennms-repo-stable-rhel7.noarch.rpm

RUN yum -y install  \
	ed \
	openssh-clients \
	unzip \
	wget \
	which \
	rsync && \
	yum -y clean all

RUN yum -y install java-1.8.0-openjdk-devel && yum -y clean all
