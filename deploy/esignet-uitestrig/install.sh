#!/bin/bash
# Installs the eSignet UI test rig (Java harness, uitest-esignet image) from
# the published mosip-helm chart repo.
## Usage: ./install.sh [kubeconfig]
#
# Mirrors ../esignet-apitestrig/install.sh's own pattern: helm repo add +
# install by name/version from mosip-helm, not a local chart path. This
# only works once helm/esignet-uitestrig has actually merged upstream and
# MOSIP's CI has published it there -- until then, `helm install` below
# will fail with "chart not found", which is expected while this is still
# on a feature branch.
#
# Installs into its OWN namespace (esignet-uitestrig), separate from
# esignet-apitestrig's shared "esignet" namespace -- this chart's own
# ConfigMap/Secret naming (via common.names.fullname) means a shared
# namespace would actually be safe too (unlike the generic mosip/uitestrig
# chart this replaces), but keeping the UI and API rigs in separate
# namespaces is a valid choice either way; best-effort defaults below are
# read from the "esignet" namespace regardless, via SOURCE_NS.
#
# Only two prompts -- everything else lives in values.yaml (tracked in git,
# edit it directly) and values.secret.yaml (gitignored, holds the consent
# DB password, Keycloak admin password, test identities/PII, and S3 keys --
# copy values.secret.yaml.example to get started).

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

set -o errexit
set -o nounset
set -o errtrace
set -o pipefail

SOURCE_NS=esignet
NS=esignet-uitestrig
RELEASE_NAME=esignet-uitestrig
CHART_VERSION=0.0.1-develop
VALUES_FILE=values.yaml
SECRET_VALUES_FILE=values.secret.yaml

# Only handles simple "key: value" / "key: \"value\"" lines under a fixed
# 4-space indent (matching this file's own layout) -- not a general YAML
# parser, just enough to read uitestrig.configMap.localeUrl's current value.
yaml_val() {
  local key="$1"
  grep -m1 -E "^    ${key}:" "$VALUES_FILE" \
    | sed -E "s/^[^:]+:[[:space:]]*['\"]?([^'\"#]*)['\"]?[[:space:]]*(#.*)?\$/\1/" \
    | sed -E 's/[[:space:]]+$//'
}

function installing_uitestrig() {
  if [[ ! -f "$SECRET_VALUES_FILE" ]]; then
    echo "ERROR: $SECRET_VALUES_FILE not found."
    echo "Copy ${SECRET_VALUES_FILE}.example to $SECRET_VALUES_FILE and fill in"
    echo "the consent DB password, Keycloak admin password, test identities,"
    echo "and S3 keys; EXITING."
    exit 1
  fi

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

  # Best-effort defaults, same derivation as esignet-apitestrig's
  # install.sh: read eSignet's own host and the api-internal host from
  # SOURCE_NS, where eSignet/esignet-apitestrig actually live (uitestrig
  # gets its own separate namespace, NS, below -- esignet-global doesn't
  # exist there). Falls back to a bare prompt if not found.
  ESIGNET_HOST=$(kubectl -n "$SOURCE_NS" get cm esignet-global -o json 2>/dev/null | jq -r '.data."mosip-esignet-host"' 2>/dev/null || true)
  API_INTERNAL_HOST=$(kubectl -n "$SOURCE_NS" get cm esignet-global -o json 2>/dev/null | jq -r '.data."mosip-api-internal-host"' 2>/dev/null || true)

  DEFAULT_ESIGNET_BASE_URL=""
  if [[ -n "$ESIGNET_HOST" && "$ESIGNET_HOST" != "null" ]]; then
    DEFAULT_ESIGNET_BASE_URL="https://$ESIGNET_HOST"
  fi
  read -rp "eSignet base URL (origin only, no path)${DEFAULT_ESIGNET_BASE_URL:+ [$DEFAULT_ESIGNET_BASE_URL]}: " ESIGNET_BASE_URL
  ESIGNET_BASE_URL="${ESIGNET_BASE_URL:-$DEFAULT_ESIGNET_BASE_URL}"
  if [[ -z "$ESIGNET_BASE_URL" ]]; then
    echo "ERROR: eSignet base URL is required; EXITING."
    exit 1
  fi

  ENV_ENDPOINT=""
  ENV_USER=""
  if [[ -n "$API_INTERNAL_HOST" && "$API_INTERNAL_HOST" != "null" ]]; then
    ENV_ENDPOINT="https://$API_INTERNAL_HOST"
    ENV_USER=$(printf '%s' "$API_INTERNAL_HOST" | awk -F '.' '/api-internal/{print $1"."$2}')
  fi
  if [[ -z "$ENV_ENDPOINT" ]]; then
    read -rp "ENV_ENDPOINT (api-internal host, e.g. https://api-internal.<env>.mosip.net): " ENV_ENDPOINT
    if [[ -z "$ENV_ENDPOINT" ]]; then
      echo "ERROR: ENV_ENDPOINT is required; EXITING."
      exit 1
    fi
  fi

  LOCALE_OPTS=()
  if [[ -z "$(yaml_val localeUrl)" ]]; then
    LOCALE_OPTS+=(--set "uitestrig.configMap.localeUrl=$ESIGNET_BASE_URL")
  fi

  read -rp "Have you reviewed/updated $VALUES_FILE for this environment? (Y/n): " values_confirmed
  values_confirmed="${values_confirmed:-y}"
  values_confirmed=$(printf '%s' "$values_confirmed" | tr '[:upper:]' '[:lower:]')
  if [[ "$values_confirmed" != "y" ]]; then
    echo "Update $VALUES_FILE first (baseurl/localeUrl, consent DB host,"
    echo "report storage, etc.), then re-run this script; EXITING."
    exit 1
  fi

  echo ""
  echo "Installing $RELEASE_NAME (mosip/esignet-uitestrig, version $CHART_VERSION) in namespace $NS ..."
  helm -n $NS upgrade --install $RELEASE_NAME mosip/esignet-uitestrig --version $CHART_VERSION \
    -f "$VALUES_FILE" \
    -f "$SECRET_VALUES_FILE" \
    --set uitestrig.configMap.eSignetbaseurl="$ESIGNET_BASE_URL" \
    --set uitestrig.extraEnvVars.ENV_ENDPOINT="$ENV_ENDPOINT" \
    --set uitestrig.extraEnvVars.ENV_USER="$ENV_USER" \
    "${LOCALE_OPTS[@]}" \
    --wait

  echo "Installed $RELEASE_NAME."
  return 0
}

installing_uitestrig
