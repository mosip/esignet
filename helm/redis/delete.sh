#!/bin/sh
# Uninstalls kafka
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=redis
while true; do
    read -p "Are you sure you want to delete redis helm chart? Y/n ?" yn
    if [ $yn = "Y" ]
      then
        helm -n $NS delete redis
        echo Deleted Redis services.
        break
      else
        break
    fi
done

