#!/bin/bash
# If you update admin password via console, call this script to update the secret in kubernetes.
# Usage:
# ./update_secrets.sh <new_admin_password> [kube_config_file]

if [ $# -ge 2 ]; then
  CLUSTER_CONFIG=$2
else
  CLUSTER_CONFIG=$HOME/.kube/iam_config
fi

function update_secret() {
  alias KK='kubectl --kubeconfig $CLUSTER_CONFIG -n keycloak'
  $KK create secret generic keycloak --from-literal=admin-password=$1 --dry-run=client -o yaml | $KK  apply -f -
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
update_secret   # calling function
