#!/bin/bash

#installs the pre-requisites.
set -e

echo "Downloading pre-requisites install scripts"
wget --no-check-certificate --no-cache --no-cookies $artifactory_url_env/artifactory/libs-release-local/i18n/esignet-i18n-bundle.zip -O $i18n_path/esignet-i18n-bundle.zip

echo "unzip pre-requisites.."
chmod 775 $i18n_path/*

cd $i18n_path
unzip -o esignet-i18n-bundle.zip

echo "unzip pre-requisites completed."

echo "Replacing public url placeholder with public url"

if [ -z "$OIDC_UI_PUBLIC_URL" ]; then
  rpCmd="s/_PUBLIC_URL_//g"
  grep -rl '_PUBLIC_URL_' $base_path/html | xargs sed -i $rpCmd
else
  mkdir $base_path/${OIDC_UI_PUBLIC_URL}
  mv  -v $base_path/html/* $base_path/$OIDC_UI_PUBLIC_URL/
  rpCmd="s/_PUBLIC_URL_/\/${OIDC_UI_PUBLIC_URL}/g"
  grep -rl '_PUBLIC_URL_' $base_path/${OIDC_UI_PUBLIC_URL} | xargs sed -i $rpCmd
fi

echo "Replacing completed."

exec "$@"
