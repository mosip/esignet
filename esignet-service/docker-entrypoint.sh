#!/bin/sh
set -e
if [ ! -f "${THUNDERID_SIGNING_KEY}" ]; then
  cert_path="$(dirname "${THUNDERID_SIGNING_KEY}")/signing.crt"
  mkdir -p "$(dirname "${THUNDERID_SIGNING_KEY}")"
  openssl req -x509 -newkey rsa:2048 \
    -keyout "${THUNDERID_SIGNING_KEY}" \
    -out "${cert_path}" \
    -days 3650 -nodes -subj "/CN=esignet"
fi
exec /app/engine
