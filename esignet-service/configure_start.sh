#!/bin/bash

#Downloads the zip/jar esignet adapters
if [[ -n "$esignet_wrapper_url_env" ]]; then
  wrapper_filename=$(basename "$esignet_wrapper_url_env")
  wget -q "${esignet_wrapper_url_env}" -O "${loader_path_env}"/"${wrapper_filename}"
  if file "${loader_path_env}"/"${wrapper_filename}" | grep -q "Zip archive"; then
    echo "Downloaded wrapper file is a zip archive. Unzipping the ${wrapper_filename}"
    unzip "${loader_path_env}"/"${wrapper_filename}" -d "${loader_path_env}"
  else
    echo "Downloaded wrapper file ${wrapper_filename} is not a zip archive."
  fi
fi

#installs the pkcs11 libraries.
set -e

# Check if $artifactory_url_env is not empty
if [[ -n "$artifactory_url_env" ]]; then
    DEFAULT_ZIP_PATH=artifactory/libs-release-local/hsm/client.zip
    [ -z "$hsm_zip_file_path" ] && zip_path="$DEFAULT_ZIP_PATH" || zip_path="$hsm_zip_file_path"

    echo "Download the client from $artifactory_url_env"
    echo "Zip File Path: $zip_path"

    wget -q --show-progress "$artifactory_url_env/$zip_path"
    echo "Downloaded $artifactory_url_env/$zip_path"
    FILE_NAME=${zip_path##*/}
    DIR_NAME=$hsm_local_dir_name
else
    FILE_NAME="client.zip"
    DIR_NAME=$hsm_local_dir
fi


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
