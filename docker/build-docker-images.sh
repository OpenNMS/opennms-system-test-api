#!/bin/sh -e
[ ! -d minion ] && echo "The script must be invoked from the docker subdirectory of the project." && exit 1

run() {
	echo "Running: $@"
	"$@"
}

echo "Pulling postgres image from public registry"
run docker pull postgres:9.5.1

echo "Building base image"
run docker build -t stests/base ./base

echo "Building Ubuntu base image"
run docker build -t stests/ubuntu-base ./ubuntu-base

if [ `ls -1 minion/rpms/*.rpm | wc -l` -gt 0 ]; then
	echo "Building Minion image"
	run docker build -t stests/minion ./minion
else
	echo "WARNING: No minion RPMs.  Skipping minion image."
fi

echo "Building OpenNMS image"
run docker build -t stests/opennms ./opennms

echo "Building snmpd image"
run docker build -t stests/snmpd ./snmpd

echo "Building Tomcat image"
run docker build -t stests/tomcat ./tomcat
