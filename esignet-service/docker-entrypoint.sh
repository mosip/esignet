#!/bin/bash

# Installs the PKCS#11 (HSM / SoftHSM pkcs11-proxy) client libraries before the
# service starts. The Go keymanager defaults to PKCS11 and loads
# /usr/local/lib/softhsm/libpkcs11-proxy.so — that .so is provided by this
# client zip, not by the base image.
set -e

KEYSTORE_TYPE="${KEYMANAGER_KEYSTORE_TYPE:-PKCS11}"
KEYSTORE_TYPE_UPPER="$(printf '%s' "$KEYSTORE_TYPE" | tr '[:lower:]' '[:upper:]')"

# File-based PKCS12 (local / mock docker-compose) does not need the HSM client.
if [[ "$KEYSTORE_TYPE_UPPER" == "PKCS12" ]]; then
  echo "*** HSM Client installation is ignored (KEYMANAGER_KEYSTORE_TYPE=PKCS12) ***"
  cd "$work_dir"
  exec "$@"
fi

# Check if $hsm_client_zip_url_env is not empty — download/replace the baked-in zip.
if [[ -n "$hsm_client_zip_url_env" ]]; then
    echo "Download the client from $hsm_client_zip_url_env"
    wget --show-progress "$hsm_client_zip_url_env" -O client.zip
    echo "Downloaded $hsm_client_zip_url_env"
fi

DIR_NAME=$hsm_local_dir_env
FILE_NAME="client.zip"

if [[ ! -f "$FILE_NAME" ]]; then
  echo "ERROR: PKCS11 keystore requires the HSM client library at /usr/local/lib/softhsm/libpkcs11-proxy.so"
  echo "Set hsm_client_zip_url_env to the client.zip URL, or rebuild the image so client.zip is baked in."
  exit 1
fi

has_parent=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | wc -l)
if test "$has_parent" -eq 1; then
  echo "Zip has a parent directory inside"
  dirname=$(zipinfo -1 "$FILE_NAME" | awk '{split($NF,a,"/");print a[1]}' | sort -u | head -n 1)
  echo "Unzip directory"
  unzip -o $FILE_NAME
  echo "Renaming directory"
  rm -rf "$DIR_NAME"
  mv -v $dirname $DIR_NAME
else
  echo "Zip has no parent directory inside"
  echo "Creating destination directory"
  mkdir -p "$DIR_NAME"
  echo "Unzip to destination directory"
  unzip -o -d "$DIR_NAME" $FILE_NAME
fi

echo "Attempting to install"
cd ./$DIR_NAME && chmod +x install.sh && sudo ./install.sh
echo "Installation complete"

cd $work_dir
exec "$@"
