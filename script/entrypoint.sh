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

echo -e "\\nInstalling additionals libs .."

curl -LJO https://github.com/geoscript/geoscript-groovy/releases/download/1.13.0/geoscript-groovy-1.13.0-zip.zip
unzip geoscript-groovy-1.13.0-zip.zip -d /additional-libs/
