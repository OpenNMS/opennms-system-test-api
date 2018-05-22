#!/bin/sh -e
if [ -z "$OPENNMS_RPM_ROOT" ]; then
  if [ -z "$OPENNMS_BUILD" ]; then
    OPENNMS_BUILD="$HOME/git/opennms"
  fi
  OPENNMS_RPM_ROOT="$OPENNMS_BUILD/target/rpm/RPMS/noarch"
fi

test -e $OPENNMS_RPM_ROOT || (echo "Cannot find RPM directory '$OPENNMS_RPM_ROOT'." && exit 1)

# Grab the release number from one of the known RPMs so we can filter the RPMs we need
RELEASE="$(rpm -q --queryformat='%{version}-%{release}' "$OPENNMS_RPM_ROOT"/opennms-core-*.noarch.rpm)"

mkdir -p opennms/rpms
rm -rf opennms/rpms/*.rpm
cp "$OPENNMS_RPM_ROOT"/opennms-webapp-jetty-"$RELEASE"*.noarch.rpm opennms/rpms/opennms-webapp-jetty.noarch.rpm
cp "$OPENNMS_RPM_ROOT"/opennms-core-"$RELEASE"*.noarch.rpm opennms/rpms/opennms-core.noarch.rpm
cp "$OPENNMS_RPM_ROOT"/opennms-"$RELEASE"*.noarch.rpm opennms/rpms/opennms.noarch.rpm

mkdir -p minion/rpms
if [ "$(find "$OPENNMS_RPM_ROOT" -name 'opennms-minion-*.rpm' | wc -l)" -gt 0 ]; then
  rm -rf minion/rpms/*.rpm
  cp "$OPENNMS_RPM_ROOT"/opennms-minion-"$RELEASE"*.noarch.rpm  minion/rpms/opennms-minion.noarch.rpm
  cp "$OPENNMS_RPM_ROOT"/opennms-minion-container-"$RELEASE"*.noarch.rpm minion/rpms/opennms-minion-container.noarch.rpm
  cp "$OPENNMS_RPM_ROOT"/opennms-minion-features-core-"$RELEASE"*.noarch.rpm minion/rpms/opennms-minion-features-core.noarch.rpm
  cp "$OPENNMS_RPM_ROOT"/opennms-minion-features-default-"$RELEASE"*.noarch.rpm minion/rpms/opennms-minion-features-default.noarch.rpm
else
  rm -rf minion/rpms/* || :
fi

mkdir -p sentinel/rpms
if [ "$(find "$OPENNMS_RPM_ROOT" -name 'opennms-sentinel-*.rpm' | wc -l)" -gt 0 ]; then
  rm -rf sentinel/rpms/*.rpm
  cp "$OPENNMS_RPM_ROOT"/opennms-sentinel-"$RELEASE"*.noarch.rpm  sentinel/rpms/opennms-sentinel.noarch.rpm
else
  rm -rf sentinel/rpms/*
fi
