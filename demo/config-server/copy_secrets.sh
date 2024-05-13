#!/bin/bash
# Copy secrets from other namespaces

function copying_secrets() {
  COPY_UTIL=../../utils/copy_cm_func.sh
  DST_NS=config-server  # DST_NS: Destination namespace
  $COPY_UTIL secret keycloak keycloak $DST_NS
  $COPY_UTIL secret keycloak-client-secrets keycloak $DST_NS
  $COPY_UTIL secret s3 s3 $DST_NS
  $COPY_UTIL secret mosip-captcha captcha $DST_NS
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
copying_secrets   # calling function