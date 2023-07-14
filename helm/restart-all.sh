#!/bin/bash

# restarts esignet services in correct order
## Usage: ./restart-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function Restarting_All() {
  ROOT_DIR=`pwd`

  declare -a module=("redis"
                     "esignet"
         "oidc-ui"
                     )

  echo restarting esignet services

  for i in "${module[@]}"
  do
    cd $ROOT_DIR/"$i"
    ./restart.sh
  done

  echo All esignet services restarted sucessfully.
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
Restarting_All   # calling function