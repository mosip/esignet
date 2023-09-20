#!/bin/bash
# Installs oidc-ui helm charts
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=1.0.1

echo Create $NS namespace
kubectl create ns $NS

function installing_oidc-ui() {
  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  echo Copy configmaps
  ./copy_cm.sh

  ESIGNET_HOST=$(kubectl get cm global -o jsonpath={.data.mosip-esignet-host})

  echo "Create configmaps oidc-ui-cm, delete if exists"
  kubectl -n $NS delete --ignore-not-found=true configmap oidc-ui-cm
  kubectl -n $NS create configmap oidc-ui-cm --from-literal="REACT_APP_API_BASE_URL=http://esignet.$NS/v1/esignet" --from-literal="OIDC_UI_PUBLIC_URL=""" --from-literal="SIGN_IN_WITH_ESIGNET_PLUGIN_URL=http://artifactory.artifactory:80/artifactory/libs-release-local/mosip-plugins/sign-in-with-esignet.zip" --from-literal="REACT_APP_SBI_DOMAIN_URI=http://esignet.$NS"

  echo Installing OIDC UI
  helm -n $NS install oidc-ui mosip/oidc-ui --set istio.hosts\[0\]=$ESIGNET_HOST

  kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

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
