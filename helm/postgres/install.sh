#!/bin/bash
# Installs postgres inside the cluster
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=postgres

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
echo Create $NS namespace
kubectl create namespace $NS
kubectl label ns $NS istio-injection=enabled --overwrite

function installing_postgres() {
  echo Installing  Postgres
  helm -n $NS install postgres bitnami/postgresql --version 13.1.5 -f values.yaml --wait
  echo Installed Postgres
  echo Installing gateways and virtual services
  POSTGRES_HOST=$(kubectl get cm global -o jsonpath={.data.mosip-postgres-host})
  helm -n $NS install istio-addons chart/istio-addons --set postgresHost=$POSTGRES_HOST --wait
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_postgres   # calling function

./init_db.sh
