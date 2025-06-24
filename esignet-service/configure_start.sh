#!/bin/bash

#Downloads the zip/jar esignet adapters
if [[ -n "$plugin_url_env" ]]; then
  plugin_zip_filename=$(basename "$plugin_url_env")
  wget -q "${plugin_url_env}" -O "${plugins_path_env}"/"${plugin_zip_filename}"
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
fi

DIR_NAME=$hsm_local_dir_env
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

IFS=',' read -ra plugin_array <<< "$plugin_name_env"
for plugin_name in "${plugin_array[@]}"; do
  plugin_name_trimmed=$(echo "$plugin_name" | xargs)  # Trim spaces
  source_file="$work_dir/plugins/$plugin_name_trimmed"
  echo "Copying plugin: $source_file to $loader_path_env"
  if [[ -f "$source_file" ]]; then
    cp "$source_file" "$loader_path_env"
    echo "Plugin '$plugin_name_trimmed' copied successfully."
  else
    echo "Error: Plugin '$plugin_name_trimmed' not found at '$source_file'."
    exit 1
  fi
done

## set active profile if not set
if [[ -z "$active_profile_env" ]]; then
  echo "Alert: active_profile_env is not set. setting to default"
  active_profile_env="default"
  export active_profile_env
fi

cd $work_dir
exec "$@"
