#!/bin/bash

# Installs Idp services in correct order
## Usage: ./install-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`

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
