#!/bin/bash

# Installs eSignet services in the correct order
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=$(pwd)

function installing_eSignet() {
  helm repo add mosip https://mosip.github.io/mosip-helm

  # esignet/install.sh prompts for the required plugin (mock/mosip/sunbird)
  # itself, so install it along with oidc-ui.
  declare -a modules=("esignet" "oidc-ui")
  echo "Installing selected modules: ${modules[*]}"

  # Install modules
  for mod in "${modules[@]}"; do
    cd "$ROOT_DIR/$mod"
    ./install.sh
  done

  echo "All selected eSignet services deployed successfully."

  if [ -f /tmp/plugin_no.txt ]; then
    plugin_no=$(cat /tmp/plugin_no.txt)
    rm /tmp/plugin_no.txt
  fi
  #echo "DEBUG: plugin_no=$plugin_no"

   # Run partner-onboarder if plugin_no is 2 (mosip-identity-plugin)
  if [[ "${plugin_no:-}" == "2" ]]; then
    echo "Deploying MISP-onboarder"
    cd "$ROOT_DIR/../partner-onboarder"
    ./install.sh
  fi

  return 0
}

# Set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialized variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_eSignet   # calling function

echo "Informational message - Please refer deployment guide to know more about the mock replying party portal installation,"
echo "having mock relying party portal installed will be helpful to verify the complete eSignet flow. "