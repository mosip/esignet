#!/bin/bash
# Installs Softhsm service for Esignet
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

SOFTHSM_NS=softhsm
SERVICE_NAME=esignet-softhsm
SOFTHSM_CHART_VERSION=12.0.1

function installing_softhsm() {
  echo Create $SOFTHSM_NS namespaces
  kubectl create ns $SOFTHSM_NS || true

  echo Istio label
  kubectl label ns $SOFTHSM_NS istio-injection=enabled --overwrite
  helm repo update

  # Deploy Softhsm for Esignet.
  echo "Installing Softhsm for esignet"
  helm -n "$SOFTHSM_NS" install $SERVICE_NAME mosip/softhsm -f softhsm-values.yaml --version "$SOFTHSM_CHART_VERSION" --wait
  echo "Installed Softhsm for esignet"

  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_softhsm   # calling function
