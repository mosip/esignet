#!/bin/bash

# Initializes prerequisite services for Esignet
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`

function prompt_for_initialisation() {
  local module_name=$1
  local prompt_message=$2
  read -p "$prompt_message (y/n): " response
  # Check for valid input
  if [[ "$response" != "y" && "$response" != "Y" && "$response" != "n" && "$response" != "N" ]]; then
    echo "Incorrect input. Please enter 'y' or 'n'."
    exit 1
  fi
  if [[ "$response" == "y" || "$response" == "Y" ]]; then
    cd $ROOT_DIR/"$module_name"
    ./"$module_name"-init.sh
  else
    echo "Skipping initialization of $module_name."
  fi
}

function initialising_prerequisites() {
  declare -a modules=("postgres" "keycloak")
  declare -A prompts=(
    ["postgres"]="Do you want to continue executing postgres init?"
    ["keycloak"]="Do you want to continue executing keycloak init?"
  )

  echo "Initializing prerequisite services"

  for module in "${modules[@]}"
  do
      prompt_for_initialisation "$module" "${prompts[$module]}"
  done


  echo "All prerequisite services initialized successfully."
  return 0
}

# Set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialized variable
set -o errtrace  ## trace ERR through 'time command' and other functions
set -o pipefail  ## trace ERR through pipes
initialising_prerequisites     ##calling function
