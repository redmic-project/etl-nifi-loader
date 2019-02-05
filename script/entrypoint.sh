#!/bin/sh

echo "Will copy following resources:"

ls -R /redmic-nifi-conf

echo "Copying resources .."

mkdir -p /nifi-conf/redmic

if cp -a /redmic-nifi-conf/. /nifi-conf/redmic
then
	echo "Resources copied to Nifi config volume successfully"
else
	echo "Error while copying resources!"
fi
