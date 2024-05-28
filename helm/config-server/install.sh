#!/bin/bash
# Installs config-server
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=config-server
CHART_VERSION=0.0.1-develop

    echo Create $NS namespace
    kubectl create ns $NS

    # set commands for error handling.
    set -e
    set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
    set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
    set -o errtrace  # trace ERR through 'time command' and other functions
    set -o pipefail  # trace ERR through pipes

    echo Istio label
    kubectl label ns $NS istio-injection=enabled --overwrite
    helm repo update

    echo Copy configmaps
    sed -i 's/\r$//' copy_cm.sh
    ./copy_cm.sh

    echo Copy secrets
    sed -i 's/\r$//' copy_secrets.sh
    ./copy_secrets.sh

    echo Installing config-server
    helm -n $NS install config-server mosip/config-server -f values.yaml --wait --version $CHART_VERSION
    echo Installed Config-server.
