#!/bin/bash

# Installs esignet services in correct order
## Usage: ./install-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`

function installing_All() {

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

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
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_All   # calling function