#!/bin/bash
# Initialize Keycloak with MOSIP base data
# Usage:
# ./keycloak_init.sh [kube_config_file]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function initialize_keycloak() {
  NS=keycloak
  CHART_VERSION=12.0.2

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  IAMHOST_URL=$(kubectl get cm global -o jsonpath={.data.mosip-iam-external-host})
  echo Initializing keycloak-init
  helm -n $NS install keycloak-init mosip/keycloak-init   \
  -f keycloak-init-values.yaml \
  --set keycloak.realms.mosip.realm_config.attributes.frontendUrl="https://$IAMHOST_URL/auth" --version $CHART_VERSION
    return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
initialize_keycloak   # calling function
