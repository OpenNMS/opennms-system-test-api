FROM ubuntu
MAINTAINER ranger@opennms.org

# pre-approve oracle license
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections

# set up APT for repository access
RUN echo 'APT::Install-Suggests "0";' > /etc/apt/apt.conf.d/60user && \
	echo 'APT::Install-Recommends "0";' >> /etc/apt/apt.conf.d/60user && \
	apt-get update && \
	apt-get -y install apt-utils apt-transport-https ca-certificates libterm-readline-gnu-perl wget && \
	apt-get -u -y dist-upgrade && \
	wget -O - https://debian.opennms.org/OPENNMS-GPG-KEY 2>/dev/null | apt-key add - && \
	apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D && \
	apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9 && \
	apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886 && \
	echo "deb http://debian.opennms.org unstable main" > /etc/apt/sources.list.d/opennms.list && \
	echo "deb https://apt.dockerproject.org/repo ubuntu-xenial main" > /etc/apt/sources.list.d/docker.list && \
	echo "deb http://debian.opennms.org/freeipa jessie-backports main" > /etc/apt/sources.list.d/freeipa.list && \
	echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" > /etc/apt/sources.list.d/webupd8team-java.list && \
	apt-get -y clean

RUN apt-get update && \
	for PACKAGE in \
		autoconf \
		automake \
		autotools-dev \
		cdbs \
		chromium-browser \
		chromium-chromedriver \
		cmake \
		createrepo \
		dpkg-dev \
		dpkg-sig \
		debsigs \
		debhelper \
		devscripts \
		docker-engine \
		expect \
		firefox \
		gnupg \
		gnupg2 \
		haveged \
		jicmp \
		jicmp6 \
		jrrd \
		jrrd2 \
		lftp \
		libdbd-pg-perl \
		libexpect-perl \
		libfile-sharedir-perl \
		libfile-slurp-perl \
		libfile-tail-perl \
		libicu-dev \
		liblist-compare-perl \
		libmodule-install-perl \
		libnet-sftp-foreign-perl \
		libpath-class-perl \
		libperl-version-perl \
		libproc-processtable-perl \
		librpm-dev \
		librrd-dev \
		libtool \
		libtool-bin \
		linux-image-extra-virtual \
		make \
		maven \
		nfs-client \
		nsis \
		ntp \
		openjdk-7-jdk \
		oracle-java8-installer \
		oracle-java8-set-default \
		phantomjs \
		pinentry-curses \
		po-debconf \
		postgresql \
		postgresql-server-dev-all \
		postgresql-server-dev-9.5 \
		r-base \
		r-cran-zoo \
		r-recommended \
		rpm \
		ruby \
		screen \
		sshpass \
		vim \
		x11-xserver-utils \
		xvfb \
		yum \
		yum-utils ; do \
			echo "* installing $PACKAGE"; \
			apt-get -y install "$PACKAGE"; \
		done && \
	apt-get -y clean


#
## Import the OpenNMS GPG key
#RUN rpm --import https://yum.opennms.org/OPENNMS-GPG-KEY
#
## Make sure we have the EPEL repository available
#RUN yum install -y epel-release deltarpm http://yum.opennms.org/repofiles/opennms-repo-stable-rhel7.noarch.rpm
#
#RUN yum install -y \
#	ed \
#	java-1.8.0-openjdk-devel \
#	openssh-clients \
#	unzip \
#	wget \
#	which \
#	rsync && \
#	yum -y clean all && \
#       rm -rf /var/cache/yum
