#!/bin/bash
# Installs redis
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=redis
CHART_VERSION=17.3.14

echo Create $NS namespace
kubectl create ns $NS

function installing_redis() {
  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite

  echo Updating helm repos
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update

  echo Installing redis
  helm -n $NS install redis bitnami/redis --wait --version $CHART_VERSION

  ../copy_cm_func.sh secret redis redis config-server

  kubectl -n config-server set env --keys=redis-password --from secret/redis deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_

  echo Installed redis service
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_redis   # calling function