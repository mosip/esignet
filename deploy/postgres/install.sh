#!/bin/bash
# Installs postgres server in K8 cluster
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

# Set namespace variable
NS=postgres

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
                echo "Exiting the installation as Postgres cannot be deployed without deleting the existing $secret_name secret."
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
    echo "Creating $NS namespace..."
    kubectl create namespace $NS || true
    kubectl label ns $NS istio-injection=enabled --overwrite

    echo "Installing Postgres..."
    helm -n $NS install postgres bitnami/postgresql --version 13.1.5 -f values.yaml --wait
    echo "Installed Postgres."

    echo "Installing gateways and virtual services..."
    POSTGRES_HOST=$(kubectl get cm global -o jsonpath={.data.mosip-postgres-host})
    helm -n $NS install istio-addons chart/istio-addons --set postgresHost=$POSTGRES_HOST --wait
    kubectl apply -f postgres-config.yaml
    return 0
}

#Prompt the user if they want to install PostgreSQL
while true; do
    read -p "Do you want to install default Postgres? (y/n): " answer
    if [ "$answer" = "Y" ] || [ "$answer" = "y" ]; then
        echo "Configuring external Postgres details by generating secrets and configmap in the postgres namespace."
        echo "Running generate_secret.py to create Postgres secrets and configmap..."
        python3 generate-secret-cm.py  # Ensure Python and the script are available in the environment
        echo "Secrets generated successfully."

        installing_postgres  # Call the installation function
        exit 0  # Exit the script after completing the installation
    elif [ "$answer" = "N" ] || [ "$answer" = "n" ]; then
        echo "Skipping Postgres server installation and configuration."
        exit 0  # Exit the script as the user chose not to install Postgres
    else
        echo "Please provide a correct option (Y or N)."
    fi
done

# Set commands for error handling
set -e
set -o errexit  ## set -e : exit the script if any statement returns a non-true return value
set -o nounset  ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
