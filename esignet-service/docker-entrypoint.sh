#!/bin/sh
set -e
signing_key="${SIGNING_KEY_PATH:-${THUNDERID_SIGNING_KEY:-${DATA_DIR:-./data}/keys/signing.key}}"
if [ ! -f "${signing_key}" ]; then
  cert_path="$(dirname "${signing_key}")/signing.crt"
  mkdir -p "$(dirname "${signing_key}")"
  openssl req -x509 -newkey rsa:2048 \
    -keyout "${signing_key}" \
    -out "${cert_path}" \
    -days 3650 -nodes -subj "/CN=esignet"
fi
export SIGNING_KEY_PATH="${signing_key}"
exec "$@"
