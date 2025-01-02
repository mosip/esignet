#!/bin/bash
# Installs redis helm chart
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function installing_redis() {

  while true; do
      read -p "Do you want to install Redis in redis namespace? (y/n) :" ans
      if [ "$ans" = "n" ] || [ "$ans" = "N" ]; then
        read -p "Please confirm if redis-config is already present in redis namespace. (y/n):" response 
        if [ "$response" = "Y" ] || [ "$response" = "y" ]; then
          echo "Skipping Redis installation as well as redis-config creation."
          exit 1
        elif [ "$response" = "N" ] || [ "$response" = "n" ]; then
          echo "Skipping Redis installation and continuing with redis-config creation."
          kubectl apply -f redis-config.yaml
          echo "redis-config configmap creation completed."
          exit 1
        else
          echo "Incorrect response. Please re-execute the redis installation script."
          exit 1
        fi
      elif [ "$ans" = "y" ] || [ "$ans" = "Y" ]; then
          break
      else
          echo "Please provide a correct option (Y or N)"
      fi
  done

  NS=redis
  CHART_VERSION=17.3.14

  echo Create $NS namespace
  kubectl create ns $NS || true

  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite

  echo Updating helm repos
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update

  echo Installing redis
  helm -n $NS install redis bitnami/redis --wait --version $CHART_VERSION

  echo Installed redis service

  kubectl apply -f redis-config.yaml

  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_redis   # calling function
