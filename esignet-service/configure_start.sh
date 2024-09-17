#!/bin/bash

#Downloads the zip/jar esignet adapters
if [[ -n "$esignet_plugin_url_env" ]]; then
  plugin_zip_filename=$(basename "$esignet_plugin_url_env")
  wget -q "${esignet_plugin_url_env}" -O "${plugins_path_env}"/"${plugin_zip_filename}"
  if file "${plugins_path_env}"/"${plugin_zip_filename}" | grep -q "Zip archive"; then
    echo "Downloaded plugins file is a zip archive. Unzipping the ${plugin_zip_filename}"
    unzip "${plugins_path_env}"/"${plugin_zip_filename}" -d "${plugins_path_env}"
  else
    echo "Downloaded plugins file ${plugin_zip_filename} is not a zip archive."
  fi
fi

#installs the pkcs11 libraries.
set -e

# Check if $hsm_client_zip_url_env is not empty
if [[ -n "$hsm_client_zip_url_env" ]]; then
    echo "Download the client from $hsm_client_zip_url_env"
    wget -q --show-progress "$hsm_client_zip_url_env" -O client.zip
    echo "Downloaded $hsm_client_zip_url_env"
    DIR_NAME=$hsm_local_dir_name
else
    DIR_NAME=$hsm_local_dir
fi

FILE_NAME="client.zip"


if [ "$active_profile_env" != "local" ]; then
  has_parent=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | wc -l)
  if test "$has_parent" -eq 1; then
    echo "Zip has a parent directory inside"
    dirname=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | head -n 1)
    echo "Unzip directory"
    unzip $FILE_NAME
    echo "Renaming directory"
    mv -v $dirname $DIR_NAME
  else
    echo "Zip has no parent directory inside"
    echo "Creating destination directory"
    mkdir "$DIR_NAME"
    echo "Unzip to destination directory"
    unzip -d "$DIR_NAME" $FILE_NAME
  fi

  echo "Attempting to install"
  cd ./$DIR_NAME && chmod +x install.sh && sudo ./install.sh
  echo "Installation complete"
else
  echo "*** HSM Client installation is ignored in local profile ***"
fi


# Check if the environment variables are set
if [[ -z "$plugin_name_env" ]]; then
  echo "Error: plugin_name_env is not set."
  exit 1
fi

source_file="$work_dir/plugins/$plugin_name_env"
echo "Copy plugin $source_file to $loader_path_env"
# Copy plugin file to the destination loader path
cp "$source_file" "$loader_path_env"
# Check if the copy was successful
if [[ $? -eq 0 ]]; then
  echo "Plugin file '$source_file' successfully copied to '$loader_path_env'."
else
  echo "Error: Failed to copy the plugin."
  exit 1
fi

cd $work_dir
exec "$@"
