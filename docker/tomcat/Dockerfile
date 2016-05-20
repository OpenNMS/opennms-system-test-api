FROM tomcat:8.0
MAINTAINER jesse@opennms.org

# Enable JMX
ENV JAVA_OPTS -Dcom.sun.management.jmxremote.port=8081 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false

# JMX
EXPOSE 8081
