#!/bin/bash
## Point config to your cluster on which you are installing IAM.
## "Usage: ./install.sh [kube_config_file]"

if [ $# -ge 1 ]; then
  export KUBECONFIG=$1
fi
NS=keycloak
SERVICE_NAME=keycloak

echo Creating $NS namespace
kubectl create ns $NS

function installing_keycloak() {
  echo Istio label
  ## TODO: enable istio injection after testing well.
  kubectl label ns $NS istio-injection=disabled --overwrite
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update

  echo Installing
  helm -n $NS install $SERVICE_NAME mosip/keycloak --version "7.1.18" --set image.repository=mosipqa/mosip-artemis-keycloak --set image.tag=develop --set image.pullPolicy=Always -f values.yaml --wait

  EXTERNAL_HOST=$(kubectl get cm global -o jsonpath={.data.mosip-iam-external-host})
  echo Install Istio gateway, virtual service
  helm -n $NS install istio-addons chart/istio-addons --set keycloakExternalHost=$EXTERNAL_HOST --set keycloakInternalHost="$SERVICE_NAME.$NS" --set service=$SERVICE_NAME
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_keycloak   # calling function
