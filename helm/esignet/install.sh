#!/bin/sh
# Installs all esignet helm charts
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=1.0.1

./keycloak-init.sh

echo Copy configmaps
./copy_cm.sh

echo Installing esignet
helm -n $NS install esignet mosip/esignet --version $CHART_VERSION

kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Installed esignet service
