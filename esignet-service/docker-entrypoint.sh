#!/bin/bash

#installs the pkcs11 libraries.
set -e

# Check if $hsm_client_zip_url_env is not empty
if [[ -n "$hsm_client_zip_url_env" ]]; then
    echo "Download the client from $hsm_client_zip_url_env"
    wget --show-progress "$hsm_client_zip_url_env" -O client.zip
    echo "Downloaded $hsm_client_zip_url_env"
fi

DIR_NAME=$hsm_local_dir_env
FILE_NAME="client.zip"

if [[ -n "$hsm_client_zip_url_env" ]]; then
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
  echo "*** HSM Client installation is ignored ***"
fi

cd $work_dir
exec "$@"
