#!/bin/sh

echo "Will copy following resources:"

find /redmic-nifi-conf

echo "\\nRemoving old resources .."

rm -rf /nifi-conf/redmic
mkdir /nifi-conf/redmic

echo "\\nCopying resources .."

if cp -a /redmic-nifi-conf/. /nifi-conf/redmic
then
	echo "\\nResources copied to Nifi config volume successfully!"
else
	echo "\\nError while copying resources!"
fi
