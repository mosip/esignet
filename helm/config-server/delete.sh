#!/bin/bash
# Uninstalls config server
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function config_server() {
  NS=config-server
  while true; do
      read -p "Are you sure you want to delete config-server helm charts?(Y/n) " yn
      if [ $yn = "Y" ]
        then
          kubectl -n $NS delete configmap global keycloak-host
          kubectl -n $NS delete secret db-common-secrets keycloak keycloak-client-secrets
          helm -n $NS delete config-server
          break
        else
          break
      fi
  done
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
config_server   # calling function