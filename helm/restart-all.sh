#!/bin/bash

# restarts esignet services in correct order
## Usage: ./restart-all.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

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
