#!/bin/bash

PROXY_HOST="proxy.internal.opennms.com"
DIG="$(command -v dig)"

if [ -z "$DIG" ]; then
	yum --quiet -y install bind-utils || exit 1
fi

if [ "$(dig "$PROXY_HOST" +short  | grep -c -E '^172.20')" -gt 0 ]; then
	echo "Using proxy host: ${PROXY_HOST}"
	if [ -e /etc/yum/pluginconf.d/fastestmirror.conf ] && [ "$(grep -c "enabled=1" /etc/yum/pluginconf.d/fastestmirror.conf)" -gt 0 ]; then
		echo "* disabling fastestmirror plugin to avoid getting different mirrors every time"
		sed -i -e 's,enabled=1,enabled=0,' /etc/yum/pluginconf.d/fastestmirror.conf || exit 1
	fi

	if [ -e /etc/yum.repos.d/CentOS-Base.repo ] && [ "$(grep -c -E "^mirror" /etc/yum.repos.d/CentOS-Base.repo)" -gt 0 ]; then
		echo "* disabling yum repo file mirror configs"
		sed -i -e 's,^mirror,#mirror,' -e 's,^metalink,#metalink,' -e 's,^#base,base,' /etc/yum.repos.d/CentOS*.repo || exit 1
	fi

	if [ "$(grep -c ip_resolve /etc/yum.conf)" -eq 0 ]; then
		echo "* forcing IPv4 resolution in yum"
		echo "ip_resolve=4" >> /etc/yum.conf
	fi

	export http_proxy="http://${PROXY_HOST}:3128"
fi

exec yum "$@"
