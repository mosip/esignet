#!/bin/sh
# Restarts the idp-binding service
## Usage: ./restart.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=idp
kubectl -n $NS rollout restart deploy idp-binding-service

kubectl -n $NS get deploy idp-binding-service -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Retarted idp-binding-service