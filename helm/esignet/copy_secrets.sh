#!/bin/bash
# Copy secrets from other namespaces
# DST_NS: Destination namespace
function copying_secrets() {
  COPY_UTIL=../esignet/copy_cm_func.sh
  #DST_NS=esignet
  $COPY_UTIL secret esignet-captcha esignet config-server
  return 0
}
# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
copying_secrets   # calling function