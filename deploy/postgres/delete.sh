#!/bin/bash
# Uninstalls all postgres resources along with server from esignet namespace  in k8 cluster.
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function deleting_postgres() {
  NS=postgres
  while true; do
      read -p "CAUTION: PVC, PV will get deleted. If your PV is not in 'Retain' mode all Postgres data will be lost. Are you sure? (Y/n): " yn
      if [ "$yn" = "Y" ] || [ "$yn" = "y" ]; then
          echo "Deleting Postgres resources..."
          helm -n $NS delete postgres || echo "Failed to delete postgres helm release"
          helm -n $NS delete istio-addons || echo "Failed to delete istio-addons helm release"
          kubectl -n $NS delete pvc data-postgres-postgresql-0 || echo "Failed to delete PVC"
          helm -n $NS delete postgres-init || echo "Failed to delete postgres-init helm release"
          kubectl -n $NS delete secret postgres-postgresql || echo "Failed to delete postgres-init secret"
          kubectl -n $NS delete secret db-common-secrets || echo "Failed to delete db-common-secrets secret"
          break
      elif [ "$yn" = "N" ] || [ "$yn" = "n" ]; then
          echo "Operation aborted. No resources were deleted."
          break
      else
          echo "Please provide a valid response (Y/n)."
      fi
  done
  return 0
}

# Set commands for error handling
set -e
set -o errexit   ## Exit the script if any statement returns a non-true return value
set -o nounset   ## Exit the script if you try to use an uninitialized variable
set -o errtrace  # Trace ERR through 'time command' and other functions
set -o pipefail  # Trace ERR through pipes

deleting_postgres   # Calling function
