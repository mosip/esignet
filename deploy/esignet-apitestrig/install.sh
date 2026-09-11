#!/bin/bash
# Installs the eSignet api-test rig (Go harness) from the published
# mosip-helm chart repo.
## Usage: ./install.sh [kubeconfig]
#
# Mirrors ../esignet/install.sh's own pattern: helm repo add + install by
# name/version from mosip-helm, not a local chart path. This only works once
# helm/esignet-apitestrig has actually merged upstream and MOSIP's CI has
# published it there -- until then, `helm install` below will fail with
# "chart not found", which is expected while this is still on a feature
# branch.
#
# Only two prompts -- everything else lives in values.yaml (tracked in git,
# edit it directly) and values.secret.yaml (gitignored, holds
# KEYCLOAK_CLIENT_SECRET / the test identity / S3 keys -- copy
# values.secret.yaml.example to get started).

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

set -o errexit
set -o nounset
set -o errtrace
set -o pipefail

NS=esignet
RELEASE_NAME=esignet-apitestrig
CHART_VERSION=0.0.1-develop
VALUES_FILE=values.yaml
SECRET_VALUES_FILE=values.secret.yaml

function installing_apitestrig() {
  if [[ ! -f "$SECRET_VALUES_FILE" ]]; then
    echo "ERROR: $SECRET_VALUES_FILE not found."
    echo "Copy ${SECRET_VALUES_FILE}.example to $SECRET_VALUES_FILE and fill in"
    echo "KEYCLOAK_CLIENT_SECRET, the test identity, and S3 keys; EXITING."
    exit 1
  fi

  # Catches the case where someone copies the example/template files and
  # runs install.sh without actually filling them in -- these placeholders
  # would otherwise reach Helm and land in real Secret/ConfigMap objects.
  if grep -qE '"changeme"' "$SECRET_VALUES_FILE" "$VALUES_FILE"; then
    echo "ERROR: $SECRET_VALUES_FILE and/or $VALUES_FILE still contain the"
    echo "\"changeme\" placeholder value."
    echo "Fill in real values for every field marked '# UPDATE ...' before"
    echo "running this script; EXITING."
    exit 1
  fi

  echo "Create $NS namespace (if it doesn't already exist)"
  kubectl create ns $NS || true

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  # Best-effort default, same as before: read eSignet's own host if it's
  # deployed in this namespace. Falls back to a bare prompt if not found.
  ESIGNET_HOST=$(kubectl -n "$NS" get cm esignet-global -o json 2>/dev/null | jq -r '.data."mosip-esignet-host"' 2>/dev/null || true)
  DEFAULT_BASE_URL=""
  if [[ -n "$ESIGNET_HOST" && "$ESIGNET_HOST" != "null" ]]; then
    DEFAULT_BASE_URL="https://$ESIGNET_HOST/v1/esignet"
  fi
  read -rp "eSignet base URL${DEFAULT_BASE_URL:+ [$DEFAULT_BASE_URL]}: " MOSIP_ESIGNET_BASE_URL
  MOSIP_ESIGNET_BASE_URL="${MOSIP_ESIGNET_BASE_URL:-$DEFAULT_BASE_URL}"
  if [[ -z "$MOSIP_ESIGNET_BASE_URL" ]]; then
    echo "ERROR: eSignet base URL is required; EXITING."
    exit 1
  fi

  read -rp "Have you reviewed/updated $VALUES_FILE for this environment? (Y/n): " values_confirmed
  values_confirmed=$(printf '%s' "$values_confirmed" | tr '[:upper:]' '[:lower:]')
  if [[ "$values_confirmed" != "y" ]]; then
    echo "Update $VALUES_FILE first (Keycloak/OTP/PMS settings, surfaces,"
    echo "report storage, etc.), then re-run this script; EXITING."
    exit 1
  fi

  echo ""
  echo "Installing $RELEASE_NAME (mosip/esignet-apitestrig, version $CHART_VERSION) in namespace $NS ..."
  helm -n $NS upgrade --install $RELEASE_NAME mosip/esignet-apitestrig --version $CHART_VERSION \
    -f "$VALUES_FILE" \
    -f "$SECRET_VALUES_FILE" \
    --set apitestrig.extraEnvVars.MOSIP_ESIGNET_BASE_URL="$MOSIP_ESIGNET_BASE_URL" \
    --wait

  echo "Installed $RELEASE_NAME."
  return 0
}

installing_apitestrig
