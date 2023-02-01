#!/bin/bash

#installs the pre-requisites.
set -e

echo "Downloading pre-requisites install scripts"
wget --no-check-certificate --no-cache --no-cookies $artifactory_url_env/artifactory/libs-release-local/i18n/idp-i18n-bundle.zip -O $i18n_path/idp-i18n-bundle.zip

echo "unzip pre-requisites.."
chmod 775 $i18n_path/*

cd $i18n_path
unzip -o idp-i18n-bundle.zip

echo "unzip pre-requisites completed."

exec "$@"