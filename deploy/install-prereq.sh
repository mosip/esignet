#!/bin/bash

# Installs prerequisite services for Esignet
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ]; then
  export KUBECONFIG=$1
fi

ROOT_DIR=$(pwd)

function prompt_for_deployment() {
  local module_name=$1
  local prompt_message=$2
  read -p "$prompt_message (y/n): " response
  # Check for valid input
  if [[ "$response" != "y" && "$response" != "Y" && "$response" != "n" && "$response" != "N" ]]; then
    echo "Incorrect input. Please enter 'y' or 'n'."
    exit 1
  fi
  if [[ "$response" == "y" || "$response" == "Y" ]]; then
    cd "$ROOT_DIR/$module_name"
    ./install.sh
  else
    echo "Skipping deployment of $module_name."
  fi
}

function installing_prerequisites() {

  # Apply esignet-global config-map
  echo "Creating esignet-global configmap in esignet namespace"
  kubectl -n esignet apply -f esignet-global-cm.yaml

  declare -a modules=("istio-gateway" "postgres" "keycloak" "kafka" "redis" "softhsm")
  declare -A prompts=(
    ["softhsm"]="Do you want to install softhsm for esignet service in softhsm namespace? Opt "n" in case it already exists in Softhsm namespace: "
    ["keycloak"]="Do you want to deploy keycloak in the keycloak namespace? Opt "n" in case it already exists in keycloak namespace : "
    ["kafka"]="Do you want to deploy Kafka in the kafka namespace? Opt "n" in case it already exists in kafka namespace : "
  )

  echo "Installing prerequisite services"

  for module in "${modules[@]}"; do
    if [ "$module" == "istio-gateway" ] || [ "$module" == "postgres" ] || [ "$module" == "redis" ]; then
      cd "$ROOT_DIR/$module"
      ./install.sh
    elif  [[ -n "${prompts[$module]}" ]]; then
      prompt_for_deployment "$module" "${prompts[$module]}"
    fi
  done
  echo "All prerequisite services deployed successfully."
  return 0
}

# Set commands for error handling.
set -e
set -o errexit   ## Exit the script if any statement returns a non-true return value
set -o nounset   ## Exit the script if you try to use an uninitialized variable
set -o errtrace  ## Trace ERR through 'time command' and other functions
set -o pipefail  ## Trace ERR through pipes

# Calling the function to start installing prerequisites
installing_prerequisites
