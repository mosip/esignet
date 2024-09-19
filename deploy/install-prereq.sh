#!/bin/bash

# Installs prerequisite services
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ]; then
  export KUBECONFIG=$1
fi

ROOT_DIR=$(pwd)
SOFTHSM_NS=softhsm
SOFTHSM_CHART_VERSION=12.0.1

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

function installing_Prerequisites() {

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  # Create esignet, softhsm namespace if not present
  kubectl create ns esignet || true
  kubectl create ns "$SOFTHSM_NS" || true

  # Apply esignet-global config-map
  kubectl apply -f esignet-global-cm.yaml

  echo "Istio label"
  kubectl label ns "$SOFTHSM_NS" istio-injection=enabled --overwrite

  # Deploy Softhsm for Esignet.
  echo "Installing Softhsm for esignet"
  helm -n "$SOFTHSM_NS" install esignet-softhsm mosip/softhsm -f softhsm-values.yaml --version "$SOFTHSM_CHART_VERSION" --wait
  echo "Installed Softhsm for esignet"

  declare -a modules=("postgres" "keycloak" "kafka" "redis" "minio")
  declare -A prompts=(
    ["keycloak"]="Do you want to deploy keycloak in the keycloak namespace?"
    ["kafka"]="Do you want to deploy Kafka in the kafka namespace?"
  )

  echo "Installing prerequisite services"

  for module in "${modules[@]}"; do
    if [ "$module" == "redis" ] || [ "$module" == "postgres" ]; then
      cd "$ROOT_DIR/$module"
      ./install.sh
    elif [ "$module" == "minio" ]; then
      read -p "Is MOSIP used as Identity Provider? (y/n): " ans
      if [[ "$ans" != "y" && "$ans" != "Y" && "$ans" != "n" && "$ans" != "N" ]]; then
        echo "Incorrect input. Please enter 'y' or 'n'."
        exit 1
      fi
      if [[ "$ans" == "y" || "$ans" == "Y" ]]; then
        cd "$ROOT_DIR/$module"
        ./install.sh
        ./cred.sh
      else
        echo "Skipping Minio deployment as MOSIP is not used as Identity Provider"
      fi

    else
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
installing_Prerequisites
