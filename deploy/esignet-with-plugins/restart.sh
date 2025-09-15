#!/bin/bash
# Restarts the esignet-with-plugins service

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function Restarting_esignet_with_plugins() {
  NS=esignet
  ESIGNET_SERVICE_NAME=esignet
  kubectl -n $NS rollout restart deploy $ESIGNET_SERVICE_NAME

  kubectl -n $NS  get deploy $ESIGNET_SERVICE_NAME -o name  |  xargs -n1 -t  kubectl -n $NS rollout status

  echo Retarted esignet-with-plugins services
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
Restarting_esignet_with_plugins   # calling function
