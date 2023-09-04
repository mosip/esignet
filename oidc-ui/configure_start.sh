#!/bin/bash

#installs the pre-requisites.
set -e

echo "Downloading pre-requisites started."

#i18n bundle
echo "Downloading i18n bundle files"
wget --no-check-certificate --no-cache --no-cookies $artifactory_url_env/artifactory/libs-release-local/i18n/esignet-i18n-bundle.zip -O $i18n_path/esignet-i18n-bundle.zip

echo "unzip i18n bundle files.."
chmod 775 $i18n_path/*

cd $i18n_path
unzip -o esignet-i18n-bundle.zip

#sign-in-button-plugin
echo "Downloading plugins"

wget --no-check-certificate --no-cache --no-cookies $SIGN_IN_WITH_ESIGNET_PLUGIN_URL -O $plugins_path/temp/sign-in-button-plugin.zip

echo "unzip plugins.."
cd $plugins_path/temp
unzip -o sign-in-button-plugin.zip

#move the required js file
mv $plugins_path/temp/sign-in-with-esignet/$plugins_format/index.js $plugins_path/sign-in-button-plugin.js

# delete temp folder
cd $plugins_path
rm -r temp

echo "Pre-requisites download completed."

echo "Replacing public url placeholder with public url"

workingDir=$nginx_dir/html
if [ -z "$OIDC_UI_PUBLIC_URL" ]; then
  rpCmd="s/_PUBLIC_URL_//g"
  grep -rl '_PUBLIC_URL_' $workingDir | xargs sed -i $rpCmd
else
  workingDir=$nginx_dir/${OIDC_UI_PUBLIC_URL}
  mkdir $workingDir
  mv  -v $nginx_dir/html/* $workingDir/
  rpCmd="s/_PUBLIC_URL_/\/${OIDC_UI_PUBLIC_URL}/g"
  grep -rl '_PUBLIC_URL_' $workingDir | xargs sed -i $rpCmd
fi

echo "Replacing completed."

exec "$@"
