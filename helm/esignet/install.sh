#!/bin/sh
# Installs all esignet helm charts
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=0.0.1

kubectl create ns $NS

helm dependency build

./keycloak-init.sh

echo Copy configmaps
./copy_cm.sh

MISPKEY=$(bash misp_key.sh)
echo "MISP License key is: $MISPKEY"

echo Setting up misp-license-key secrets
kubectl -n $NS create secret generic misp-license-key --from-literal=mosip-esignet-misp-key=$MISPKEY --dry-run=client -o yaml | kubectl apply -f -

./copy_cm_func.sh secret misp-license-key esignet config-server

kubectl -n config-server set env --keys=mosip-esignet-misp-key --from secret/misp-license-key deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_

kubectl -n config-server get deploy -o name |  xargs -n1 -t  kubectl -n config-server rollout status

echo Installing esignet
helm -n $NS install esignet . --version $CHART_VERSION

kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Installed esignet service
