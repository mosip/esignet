#!/bin/bash
## Deletes  kafka helm chart
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function deleting_kafka() {
  NS=kafka
  while true; do
      read -p "CAUTION: PVC, PV will get deleted. If your PV is not in 'Retain' mode all data will be lost. Are you sure ? Y/n ?" yn
      if [ $yn = "Y" ]
        then
          helm -n $NS delete kafka
          helm -n $NS delete kafka-ui
          helm -n $NS delete istio-addons
          kubectl -n $NS delete pvc -l app.kubernetes.io/name=kafka
          kubectl -n $NS delete pvc -l app.kubernetes.io/name=zookeeper
          echo Deleted kafka and kafka-ui services.
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
deleting_kafka   # calling function

