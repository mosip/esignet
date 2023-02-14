#!/bin/sh
# Installs all idp helm charts
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=idp
CHART_VERSION=0.0.1

echo Create $NS namespace
kubectl create ns $NS

echo Istio label
kubectl label ns $NS istio-injection=enabled --overwrite

read -p "Please provide IDP host (ex: idp.sandbox.xyz.net ): " IDP_HOST

if [ -z $IDP_HOST ]; then
  echo "IDP host not provided; EXITING;"
  exit 1;
fi

kubectl -n default get cm global -o json | jq ".data[\"mosip-idp-host\"]=\"$IDP_HOST\"" | kubectl apply -f -
echo Copy configmaps
./copy_cm.sh
./keycloak-init.sh


echo "Create configmaps mockida, delete if exists"
kubectl -n $NS  --ignore-not-found=true delete cm mock-auth-data
kubectl -n $NS create cm mock-auth-data --from-file=./mock-auth-data/

echo Update IDP chart
helm dependency update
echo Installing IDP
helm -n $NS install idp . --version $CHART_VERSION


kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Installed IDP service
