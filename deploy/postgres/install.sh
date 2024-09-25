#!/bin/bash
# Installs postgres server in K8 cluster in esignet namespace
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet

# Function to check and delete secret if it exists
function check_and_delete_secret() {
  local secret_name=$1
  local secret_namespace=$2
  
  if kubectl -n $secret_namespace get secret $secret_name > /dev/null 2>&1; then
      echo "Secret $secret_name exists in namespace $secret_namespace."
      while true; do
          read -p "Do you want to delete secret $secret_name before installation? (Y/n): " yn
          if [ "$yn" = "Y" ] || [ "$yn" = "y" ]; then
              echo "Deleting secret $secret_name..."
              kubectl -n $secret_namespace delete secret $secret_name || { echo "Failed to delete secret $secret_name"; exit 1; }
              break
          elif [ "$yn" = "N" ] || [ "$yn" = "n" ]; then
              echo "Exiting the installation as postgres can't be deployed without deleting the existing $secret_name secret."
              exit 1
          else
              echo "Please provide a valid response (Y/n)."
          fi
      done
  else
      echo "Secret $secret_name does not exist in namespace $secret_namespace. Proceeding with installation."
  fi
}

function installing_postgres() {
  # Check and handle the existing secret
  check_and_delete_secret "esignet-postgres-postgresql" $NS

  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update
  echo Create $NS namespace
  kubectl create namespace $NS || true
  kubectl label ns $NS istio-injection=enabled --overwrite

  echo Installing  Postgres
  helm -n $NS install esignet-postgres bitnami/postgresql --version 13.1.5 -f values.yaml --wait
  echo Installed Postgres
  echo Installing gateways and virtual services
  POSTGRES_HOST=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-postgres-host})
  helm -n $NS install istio-addons chart/istio-addons --set postgresHost=$POSTGRES_HOST --wait
  kubectl apply -f postgres-config.yaml
  return 0
}

# Prompt the user if they want to install PostgreSQL
while true; do
    read -p "Do you want to install Postgres? Opt for 'n' if you have Postgres already installed. (y/n): " answer
    if [ "$answer" = "Y" ] || [ "$answer" = "y" ]; then
        echo "Continuing with Postgres server deployment..."
        break
    elif [ "$answer" = "N" ] || [ "$answer" = "n" ]; then
        echo "Skipping Postgres installation. Running generate_secret.py to create Postgres secrets..."
        python3 generate-secret-cm.py  # Ensure that Python and the script are available in the environment
        echo "Secrets generated. Exiting script."
        exit 0  # Exit the script after generating secrets
    else
        echo "Please provide a correct option (Y or N)"
    fi
done

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_postgres   # calling installtion postgres function.
