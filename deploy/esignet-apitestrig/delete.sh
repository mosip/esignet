#!/bin/bash
# Uninstalls the eSignet api-test rig (Go harness).
## Usage: ./delete.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
RELEASE_NAME=esignet-apitestrig

function deleting_apitestrig() {
  read -rp "Are you sure you want to delete $RELEASE_NAME in namespace $NS? (Y/n) " yn
  if [[ "$yn" == "Y" || "$yn" == "y" ]]; then
    helm -n "$NS" delete "$RELEASE_NAME"

    echo ""
    echo "NOTE: reports remain in the configured S3 bucket after 'helm delete'."
    echo "Reports contain the test identity (PII). Apply the bucket retention"
    echo "policy or delete the report objects when they are no longer needed."
  fi
  return 0
}

set -o errexit
set -o nounset
set -o errtrace
set -o pipefail
deleting_apitestrig
