#!/bin/bash

# Installs esignet services in correct order
## Usage: ./install-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`

CHART_VERSION=12.0.1-B2

echo Installing Softhsm for esignet
helm -n $SOFTHSM_NS install softhsm-esignet mosip/softhsm -f softhsm-values.yaml --version $CHART_VERSION --wait
echo Installed Softhsm for esignet

./copy_cm_func.sh secret softhsm-esignet softhsm config-server

kubectl -n config-server set env --keys=security-pin --from secret/softhsm-esignet deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_SOFTHSM_ESIGNET_

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
