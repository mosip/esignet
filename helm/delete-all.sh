#!/bin/bash

# deletes esignet services in correct order
## Usage: ./delete-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function Deleting_All() {
  ROOT_DIR=`pwd`
  SOFTHSM_NS=softhsm

  helm -n $SOFTHSM_NS delete softhsm-esignet

  declare -a module=("redis"
                     "esignet"
         "oidc-ui"
                     )

  echo Installing esignet services

  for i in "${module[@]}"
  do
    cd $ROOT_DIR/"$i"
    ./delete.sh
  done

  echo All esignet services deleted sucessfully.
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
Deleting_All   # calling function