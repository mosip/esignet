#!/bin/bash
# Installs postgres server in K8 cluster in esignet namespace
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=postgres

# Function to check and delete secret if it exists
function check_and_delete_secret() {
  local secret_name=$1
  local secret_namespace=$2
  
  if kubectl -n $secret_namespace get secret $secret_name > /dev/null 2>&1; then
      echo "Secret $secret_name exists in namespace $secret_namespace."
      while true; do
  POSTGRES_HOST=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-postgres-host})
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
  check_and_delete_secret "postgres-postgresql" $NS

  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update
  echo Create $NS namespace
  kubectl create namespace $NS || true
  kubectl label ns $NS istio-injection=enabled --overwrite

  echo Installing  Postgres
  helm -n $NS install postgres bitnami/postgresql --version 13.1.5 \
  -f values.yaml \
  --set image.repository=mosipqa/postgresql \
  --set image.tag=14.2.0-debian-10-r70 \
  --set image.pullPolicy=Always \
  --wait
  # Run the Python script to generate secrets and configmap
  if [ -f generate-secret-cm.py ]; then
      echo "Running generate_secret.py to create Postgres secrets and configmap..."
      python3 generate-secret-cm.py || { echo "Failed to run generate_secret.py"; exit 1; }
      echo "Secrets and configmap generated successfully."
  else
      echo "Error: generate-secret-cm.py not found. Ensure the script is in the current directory."
      exit 1
  fi
  echo Installing gateways and virtual services
  POSTGRES_HOST=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-postgres-host})
  helm -n $NS install istio-addons chart/istio-addons --set postgresHost=$POSTGRES_HOST --wait --timeout 3m
  return 0
}

# Prompt the user if they want to install PostgreSQL
while true; do
    read -p "Do you want to deploy postgres in the postgres namespace? Please opt for 'n' if you already have a postgres server deployed : Press enter for default y: " answer
    answer=${answer:-Y}
    if [ "$answer" = "Y" ] || [ "$answer" = "y" ]; then
        echo "Continuing with Postgres server deployment..."
        break  # Proceed with the installation
    elif [ "$answer" = "N" ] || [ "$answer" = "n" ]; then
        # Prompt the user for further options
        while true; do
            echo "You opted not to install Postgres. What would you like to do next?"
            echo "1. Skip Postgres server installation and configuration."
            echo "2. Configure external Postgres details by generating secrets and configmap ."

            read -p "Enter your choice (1/2): " option

            if [ "$option" = "1" ]; then
                echo "Skipping Postgres server installation and configuration in namespace."
                exit 0  # Exit the script as the user chose to skip Postgres installation
            elif [ "$option" = "2" ]; then
                echo "Running generate_secret.py to create Postgres secrets and configmap..."
                python3 generate-secret-cm.py  # Ensure Python and the script are available in the environment
                echo "Secrets generated successfully."
                exit 0  # Exit the script after generating secrets and configmap
            else
                echo "Not a correct option. Please try again or press Ctrl + C to exit."
            fi
        done
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
