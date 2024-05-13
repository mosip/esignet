#!/bin/bash
# Initialize Imported Keycloak with MOSIP base data
# Usage:
# ./import-init.sh [kube_config_file]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function import_init() {
  NS=keycloak
  CHART_VERSION=12.0.1

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  IAM_HOST=$(kubectl get cm global -o jsonpath={.data.mosip-iam-external-host})

  echo Initializing keycloak
  helm -n $NS install keycloak-import mosip/keycloak-init --set frontend=https://$IAM_HOST/auth -f import-init-values.yaml --version $CHART_VERSION
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
import_init   # calling function

