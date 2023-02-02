#!/bin/sh
# Restart the idp-ui services

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=idp
kubectl -n $NS rollout restart deploy idp-ui

kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Retarted idp services
