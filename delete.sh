#!/bin/sh
# Uninstalls oidc-server idp-binding-service
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=idp
while true; do
    read -p "Are you sure you want to delete idp-binding-service helm charts?(Y/n) " yn
    if [ $yn = "Y" ]
      then
        helm -n $NS delete idp-binding-service
        break
      else
        break
    fi
done