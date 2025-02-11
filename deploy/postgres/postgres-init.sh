#!/bin/bash
# Script to initialize the Esignet DB in postgres server.
## Usage: ./init_db.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function initialize_db() {
  NS=esignet
  CHART_VERSION=12.0.1
  helm repo update

  while true; do
      read -p "Please confirm with "Y" once init-values.yaml is updated correctly with tag, postgres host, and password details else "N" to exit installation: " ans
      if [ "$ans" = "Y" ] || [ "$ans" = "y" ]; then
          break
      elif [ "$ans" = "N" ] || [ "$ans" = "n" ]; then
          exit 1
      else
          echo "Please provide a correct option (Y or N)"
      fi
  done

  while true; do
      read -p "CAUTION: all existing data if any for mosip_esignet will be lost. Are you sure? (Y/n)" yn
      if [ $yn = "Y" ] || [ $yn = "y" ];
        then
          echo Removing existing mosip_esignet installation and secret
          helm -n $NS delete esignet-postgres-init || true
          kubectl -n $NS delete secret db-common-secrets  || true
	  ../copy_cm_func.sh secret postgres-postgresql postgres $NS
          echo Initializing DB
          helm -n $NS install postgres-init mosip/postgres-init --version $CHART_VERSION -f init_values.yaml --wait --wait-for-jobs
          break
      elif [ "$yn" = "N" ] || [ "$yn" = "n" ]; then
          echo "Skipping esignet postgres DB initialisation as per your input"
          break
      else
          echo "Incorrect Input. Please choose again"
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
initialize_db   # calling function
