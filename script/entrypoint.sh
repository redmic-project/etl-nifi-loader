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
echo ${ADDITIONAL_LIBS_URLS}
for url in "${ADDITIONAL_LIBS_URLS}"; do
	echo "\\nDownloading library from ${url}"
	if curl -LJO ${url}
	then
		if unzip *.zip -o -d /additional-libs/
		then
			echo -e "\\nFile decompressed successfully"
			rm *.zip
		else
			echo -e "\\nError while decompressing file"
			exit 1
		fi
	else
		echo -e "\\nError while downloading file"
		exit 1
	fi
done

echo -e "\\nAdditionals libs installed"

chown -R ${NIFI_UID}:${NIFI_GID} /additional-libs