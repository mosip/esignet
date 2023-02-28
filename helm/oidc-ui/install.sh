#!/bin/sh
# Installs oidc-ui helm charts
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=0.0.1

echo Create $NS namespace
kubectl create ns $NS

echo Istio label
kubectl label ns $NS istio-injection=enabled --overwrite
helm dependency build

echo Copy configmaps
./copy_cm.sh

ESIGNET_HOST=$(kubectl get cm global -o jsonpath={.data.mosip-esignet-host})

echo "Create configmaps oidc-ui-cm, delete if exists"
kubectl -n $NS delete --ignore-not-found=true configmap oidc-ui-cm
kubectl -n $NS create configmap oidc-ui-cm --from-literal="REACT_APP_API_BASE_URL=http://$ESIGNET_HOST/v1/esignet" --from-literal="REACT_APP_SBI_DOMAIN_URI=http://$ESIGNET_HOST"

echo Installing OIDC UI
helm -n $NS install oidc-ui . --set istio.hosts\[0\]=$ESIGNET_HOST

kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Installed oidc-ui
