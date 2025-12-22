#!/bin/bash
# Installs oidc-ui helm chart
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function installing_oidc-ui() {

  while true; do
      read -p "Do you want to continue installing OIDC ui? (y/n) :" ans
      if [ "$ans" = "Y" ] || [ "$ans" = "y" ]; then
          break
      elif [ "$ans" = "N" ] || [ "$ans" = "n" ]; then
          exit 1
      else
          echo "Please provide a correct option (Y or N)"
      fi
  done


 # Prompt for theme
  read -p "Please provide the theme for the eSignet UI. Please choose between ‘blue' or 'orange’ for esignet default theme : Press enter for the default theme. Please provide URL for the custom theme: " theme

  # Prompt for default language
  echo "Available languages: en, fr, ara"
  read -p "Please choose the default language for eSignet (press enter for 'en'): " default_lang
  if [[ -z "$default_lang" ]]; then
    default_lang="en"
  fi

  # Prompt for ID Provider name
  read -p "Please provide the name for eSignet : Note: This name would be used instead of eSignet on the login page and in other places: " id_provider_name
  if [[ -z "$id_provider_name" ]]; then
    id_provider_name="eSignet"
  fi

  NS=esignet
  OIDCUI_SERVICE_NAME=oidc-ui
  ESIGNET_SERVICE_NAME=esignet
  CHART_VERSION=1.7.1-develop

  echo Create $NS namespace
  kubectl create ns $NS || true

  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  COPY_UTIL=../copy_cm_func.sh

  ESIGNET_HOST=$(kubectl -n $NS get cm esignet-global -o jsonpath={.data.mosip-esignet-host})

  echo Installing OIDC UI
  helm -n $NS install $OIDCUI_SERVICE_NAME mosip/oidc-ui  \
    --version $CHART_VERSION \
    --set istio.hosts[0]=$ESIGNET_HOST \
    --set oidc_ui.oidc_service_host="$ESIGNET_SERVICE_NAME.$NS" \
    --set oidc_ui.configmaps.oidc-ui.REACT_APP_API_BASE_URL="http://$ESIGNET_SERVICE_NAME.$NS/v1/esignet" \
    --set oidc_ui.configmaps.oidc-ui.REACT_APP_SBI_DOMAIN_URI="http://$ESIGNET_SERVICE_NAME.$NS" \
    --set oidc_ui.configmaps.oidc-ui.DEFAULT_THEME="$theme" \
    --set oidc_ui.configmaps.oidc-ui.DEFAULT_LANG="$default_lang" \
    --set oidc_ui.configmaps.oidc-ui.DEFAULT_ID_PROVIDER_NAME="$id_provider_name"

  kubectl -n $NS  get deploy $OIDCUI_SERVICE_NAME -o name |  xargs -n1 -t  kubectl -n $NS rollout status

  echo Installed oidc-ui
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_oidc-ui   # calling function
