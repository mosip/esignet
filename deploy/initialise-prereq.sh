#!/bin/bash

# Initialises prerequisite services for Esignet
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`
NS=esignet

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
    echo "Skipping initialising of $module_name."
  fi
}

function initialising_Prerequisites() {

  declare -a modules=("postgres" "keycloak" )
  declare -A prompts=(
    ["postgres"]="Do you want to continue executing postgres init?"
    ["keycloak"]="Do you want to continue executing keycloak init?"
  )

  echo "Initialising prerequisite services"

  for module in "${modules[@]}"
  do
      prompt_for_initialisation "$module" "${prompts[$module]}"
  done

  ESIGNET_HOST=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-esignet-host})
  echo Please enter the recaptcha admin site key for domain $ESIGNET_HOST
  read ESITE_KEY
  echo Please enter the recaptcha admin secret key for domain $ESIGNET_HOST
  read ESECRET_KEY

  echo Setting up captcha secrets
  kubectl -n $NS create secret generic esignet-captcha --from-literal=esignet-captcha-site-key=$ESITE_KEY --from-literal=esignet-captcha-secret-key=$ESECRET_KEY --dry-run=client -o yaml | kubectl apply -f -

  echo Setting up dummy values for esignet misp license key
  kubectl -n $NS create secret generic esignet-misp-onboarder-key --from-literal=mosip-esignet-misp-key='' --dry-run=client -o yaml | kubectl apply -f -

  echo "All prerequisite services initialised successfully."
  return 0
}

# Set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialized variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
initialising_Prerequisites   # calling function
