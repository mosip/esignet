#!/bin/sh
# Uninstalls idp-ui helm charts
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi
NS=idp
while true; do
    read -p "Are you sure you want to delete all idp helm charts?(Y/n) " yn
    if [ $yn = "Y" ]
      then
        helm -n $NS delete idp-ui
        break
      else
        break
    fi
done
