#!/bin/bash

# Installs Idp services in correct order
## Usage: ./install-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`
NS=softhsm
CHART_VERSION=12.0.1-B2
echo Installing Softhsm for IDP
helm -n $NS install softhsm-idp mosip/softhsm -f softhsm-values.yaml --version $CHART_VERSION --wait
echo Installed Softhsm for IDP

./copy_cm_func.sh secret softhsm-idp softhsm config-server

kubectl -n config-server set env --keys=security-pin --from secret/softhsm-idp deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_SOFTHSM_IDP_

kubectl -n config-server get deploy -o name |  xargs -n1 -t  kubectl -n config-server rollout status

declare -a module=("redis"
                   "idp"
                   "idp-binding"
                   )

echo Installing IDP services

for i in "${module[@]}"
do
  cd $ROOT_DIR/"$i"
  ./install.sh
done

echo All IDP services deployed sucessfully.
