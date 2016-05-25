#!/bin/sh -e
[ ! -d minion ] && echo "The script must be invoked from the docker subdirectory of the project." && exit 1

echo "Pulling postgres image from public registry"
docker pull postgres:9.5.1

echo "Building base image"
docker build -t stests/base ./base

echo "Building Minion image"
docker build -t stests/minion ./minion

echo "Building OpenNMS image"
docker build -t stests/opennms ./opennms

echo "Building snmpd image"
docker build -t stests/snmpd ./snmpd

echo "Building Tomcat image"
docker build -t stests/tomcat ./tomcat
