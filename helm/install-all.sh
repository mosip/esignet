#!/bin/bash

# Installs Idp services in correct order
## Usage: ./install-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`

CHART_VERSION=12.0.1-B2
echo Installing Softhsm for IDP
helm -n $NS install softhsm-idp mosip/softhsm -f softhsm-values.yaml --version $CHART_VERSION --wait
echo Installed Softhsm for IDP


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
