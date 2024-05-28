#!/bin/bash

# Installs prerequisite services
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`

function prompt_for_deployment() {
  local module_name=$1
  local prompt_message=$2
  read -p "$prompt_message (y/n): " response
  if [[ "$response" == "y" || "$response" == "Y" ]]; then
    cd $ROOT_DIR/"$module_name"
    ./install.sh
  else
    echo "Skipping deployment of $module_name."
  fi
}

function installing_Prerequisites() {

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  # Apply global config-map
  kubectl apply -f global_configmap.yaml

  declare -a modules=("postgres" "iam" "kafka" "redis")
  declare -A prompts=(
    ["postgres"]="Do you want to deploy a new PostgreSQL instance or use an existing one?"
    ["iam"]="Do you want to deploy IAM or use an existing instance?"
    ["kafka"]="Do you want to deploy Kafka or use an existing instance?"
  )

  echo "Installing prerequisite services"

  for module in "${modules[@]}"
  do
    if [ "$module" == "redis" ]; then
      cd $ROOT_DIR/"$module"
      ./install.sh
    else
      prompt_for_deployment "$module" "${prompts[$module]}"
    fi
  done

  echo "All prerequisite services deployed successfully."
  return 0
}

# Set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialized variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_Prerequisites   # calling function
