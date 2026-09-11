#!/bin/bash
# Uninstalls uitestrig
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function deleting_uitestrig() {
  NS=esignet-uitestrig
  while true; do
      read -p "Are you sure you want to delete uitestrig helm charts?(Y/n) " yn
      if [ "$yn" = "Y" ] || [ "$yn" = "y" ]
        then
          helm -n $NS delete esignet-uitestrig
          break
        else
          break
      fi
  done
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
deleting_uitestrig   # calling function
