#!/bin/sh
# Installs all esignet helm charts
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
SOFTHSM_NS=softhsm

CHART_VERSION=0.0.1
SOFTHSM_CHART_VERSION=12.0.1-B2

echo Create $NS namespace
kubectl create ns $NS

echo Istio label
kubectl label ns $NS istio-injection=enabled --overwrite
helm repo update

echo Installing Softhsm for esignet
helm -n $SOFTHSM_NS install softhsm-esignet mosip/softhsm -f softhsm-values.yaml --version $SOFTHSM_CHART_VERSION --wait
echo Installed Softhsm for esignet

./copy_cm_func.sh secret softhsm-esignet softhsm config-server

kubectl -n config-server set env --keys=security-pin --from secret/softhsm-esignet deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_SOFTHSM_ESIGNET_

kubectl -n config-server get deploy -o name |  xargs -n1 -t  kubectl -n config-server rollout status

echo Copy configmaps
./copy_cm.sh

echo "Create configmaps esignet-ui-cm, delete if exists"
kubectl -n $NS delete --ignore-not-found=true configmap esignet-ui-cm
kubectl -n $NS create configmap esignet-ui-cm --from-literal="REACT_APP_API_BASE_URL=http://$ESIGNET_HOST/v1/esignet" --from-literal="REACT_APP_SBI_DOMAIN_URI=http://$ESIGNET_HOST"

echo "Create configmaps mockida, delete if exists"
kubectl -n $NS  --ignore-not-found=true delete cm mock-auth-data
kubectl -n $NS create cm mock-auth-data --from-file=./mock-auth-data/

echo Installing esignet
helm -n $NS install esignet /home/techno-422/Desktop/MOSIP/idp/helm/esignet --version $CHART_VERSION


kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Installed esignet service
