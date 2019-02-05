#!/bin/sh

echo "Will copy following resources:"

find /redmic-nifi-conf

echo -e "\\nRemoving old resources .."

rm -rf /nifi-conf/redmic
mkdir /nifi-conf/redmic

echo -e "\\nCopying resources .."

if cp -a /redmic-nifi-conf/. /nifi-conf/redmic
then
	echo -e "\\nResources copied to Nifi config volume successfully!"
else
	echo -e "\\nError while copying resources!"
fi
