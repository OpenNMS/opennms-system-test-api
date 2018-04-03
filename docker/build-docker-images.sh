#!/bin/sh -e
[ ! -d minion ] && echo "The script must be invoked from the docker subdirectory of the project." && exit 1

run() {
	echo "Running: $@"
	"$@"
}

echo "Pulling postgres image from public registry"
run docker pull postgres:9.5.1

echo "Pulling kafka 0.10.1.0 with scala 2.11 image from public registry"
run docker pull spotify/kafka@sha256:cf8f8f760b48a07fb99df24fab8201ec8b647634751e842b67103a25a388981b

echo "Pulling elasticsearch images from public registry"
run docker pull elasticsearch:2-alpine
run docker pull elasticsearch:5-alpine
run docker pull docker.elastic.co/elasticsearch/elasticsearch-oss:6.2.3

echo "Building base image"
run docker build -t stests/base ./base

echo "Building OpenNMS base image"
run docker build -t stests/opennms-base ./opennms-base

#echo "Building Ubuntu base image"
#run docker build -t stests/ubuntu-base ./ubuntu-base

if [ `find minion/rpms -name \*.rpm | wc -l` -gt 0 ]; then
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
