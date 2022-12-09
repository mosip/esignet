#!/bin/sh
# Copy configmaps from other namespaces
# DST_NS: Destination namespace

COPY_UTIL(){
  if [ $1 = "configmap" ]; then
    RESOURCE=configmap
  elif [ $1 = "secret" ]; then
    RESOURCE=secret
  else
    echo "Incorrect resource $1. Exiting.."
    exit 1
  fi

  if [ $# -ge 5 ]; then
    kubectl -n $4 delete --ignore-not-found=true $RESOURCE $5
    kubectl -n $3 get $RESOURCE $2 -o yaml | sed "s/namespace: $3/namespace: $4/g" | sed "s/name: $2/name: $5/g" | kubectl -n $4 create -f -
  else
    kubectl -n $4 delete --ignore-not-found=true $RESOURCE $2
    kubectl -n $3 get $RESOURCE $2 -o yaml | sed "s/namespace: $3/namespace: $4/g" |  kubectl -n $4 create -f -
  fi
}

COPY_UTIL=../../copy_cm_func.sh
DST_NS=idp


COPY_UTIL configmap global default $DST_NS
