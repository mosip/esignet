#!/bin/sh
# Restart the esignet services

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
kubectl -n $NS rollout restart deploy  esignet

kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Retarted esignet services
