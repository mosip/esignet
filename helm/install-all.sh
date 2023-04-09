#!/bin/bash

# Installs esignet services in correct order
## Usage: ./install-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`

SOFTHSM_NS=softhsm
SOFTHSM_CHART_VERSION=12.0.2

echo Create $SOFTHSM_NS namespace
kubectl create ns $SOFTHSM_NS

echo Istio label
kubectl label ns $SOFTHSM_NS istio-injection=enabled --overwrite
helm repo add mosip https://mosip.github.io/mosip-helm
helm repo update

echo Installing Softhsm for esignet
helm -n $SOFTHSM_NS install softhsm-esignet mosip/softhsm -f softhsm-values.yaml --version $SOFTHSM_CHART_VERSION --wait
echo Installed Softhsm for esignet

MISPKEY=$(bash misp_key.sh)
echo "MISP License key is: $MISPKEY"

echo Setting up misp-license-key secrets
kubectl -n $NS create secret generic misp-license-key --from-literal=mosip-esignet-misp-key=$MISPKEY --dry-run=client -o yaml | kubectl apply -f -

echo Copy configmaps
./copy_cm_func.sh configmap global default config-server

echo Copy secrets
./copy_cm_func.sh secret softhsm-esignet softhsm config-server
./copy_cm_func.sh secret misp-license-key esignet config-server

kubectl -n config-server set env --keys=mosip-esignet-host --from configmap/global deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_
kubectl -n config-server set env --keys=security-pin --from secret/softhsm-esignet deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_SOFTHSM_ESIGNET_
kubectl -n config-server set env --keys=mosip-esignet-misp-key --from secret/misp-license-key deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_
kubectl -n config-server get deploy -o name |  xargs -n1 -t  kubectl -n config-server rollout status


declare -a module=("redis"
                   "esignet"
		           "oidc-ui"
                   )

echo Installing esignet services

for i in "${module[@]}"
do
  cd $ROOT_DIR/"$i"
  ./install.sh
done

echo All esignet services deployed sucessfully.
