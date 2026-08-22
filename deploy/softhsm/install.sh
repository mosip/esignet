#!/bin/bash
# Installs HSM service for Esignet (SoftHSM or Hardware HSM)
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SOFTHSM_NS=esignet
SOFTHSM_CHART_VERSION=12.0.1

function installing_softhsm() {
  echo "Create $SOFTHSM_NS namespaces"
  kubectl create ns $SOFTHSM_NS || true

  echo "Istio label"
  kubectl label ns $SOFTHSM_NS istio-injection=enabled --overwrite
#  helm repo update

  echo "Installing Softhsm for esignet"
  helm -n "$SOFTHSM_NS" install esignet-softhsm mosip/softhsm -f "$SCRIPT_DIR/softhsm-values.yaml" --version "$SOFTHSM_CHART_VERSION" --wait
  echo "Installed Softhsm for esignet"

  return 0
}

function prompt_hsm_choice() {
  echo ""
  echo "Which HSM deployment do you want to use?"
  echo "  1) SoftHSM (software-based, installed via Helm)"
  echo "  2) Hardware HSM"
  echo ""
  read -rp "Enter your choice [1-2]: " HSM_CHOICE

  case "$HSM_CHOICE" in
    1)
      installing_softhsm
      ;;
    2)
      echo ""
      echo "Hardware HSM setup is not available yet. Please check back later or contact the platform team for assistance."
      echo "Exiting without making any changes."
      exit 0
      ;;
    *)
      echo "Invalid choice: '$HSM_CHOICE'. Please enter 1 or 2."
      prompt_hsm_choice
      ;;
  esac
}

set -e
set -o errexit
set -o nounset
set -o errtrace
set -o pipefail

prompt_hsm_choice