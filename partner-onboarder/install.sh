#!/bin/bash
# Onboards default partners 
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ]; then
  export KUBECONFIG=$1
fi

read -p "Is MOSIP used as Identity Provider? (Y/n): " ans
if [[ "$ans" == "n" || "$ans" == "N" ]]; then
  echo "Since MOSIP is not the Identity Provider here, Onboarder execution is not required."
  exit 1
elif [[ "$ans" != "y" && "$ans" != "Y" ]]; then
  echo "Please choose between (Y,y) or (N,n)"
  exit 1
fi

echo "Do you have a public domain & valid SSL? (Y/n)"
echo "Y: if you have a public domain & valid SSL certificate"
echo "n: If you don't have a public domain and a valid SSL certificate. Note: It is recommended to use this option only in development environments."
read -p "" flag

if [ -z "$flag" ]; then
  echo "'flag' was not provided; EXITING;"
  exit 1
fi

ENABLE_INSECURE=''
if [ "$flag" = "n" ]; then
  ENABLE_INSECURE='--set onboarding.configmaps.onboarding.ENABLE_INSECURE=true'
fi

NS=esignet
CHART_VERSION=0.0.1-develop

echo "Create $NS namespace"
kubectl create ns $NS || true

function installing_onboarder() {

  read -p "Is values.yaml for the onboarder chart set correctly as part of prerequisites? (Y/n): " yn
  if [[ "$yn" == "Y" || "$yn" == "y" ]]; then
    echo "Istio label"
    kubectl label ns $NS istio-injection=disabled --overwrite
    helm repo update

    echo "Copy configmaps and secrets"
    kubectl -n $NS --ignore-not-found=true delete cm s3
    kubectl -n $NS delete cm --ignore-not-found=true onboarding

    COPY_UTIL=../deploy/copy_cm_func.sh
    $COPY_UTIL configmap keycloak-env-vars keycloak $NS
    $COPY_UTIL configmap keycloak-host keycloak $NS

    $COPY_UTIL secret s3 s3 $NS
    $COPY_UTIL secret keycloak keycloak $NS
    $COPY_UTIL secret keycloak-client-secrets keycloak $NS

    s3_url=$(kubectl -n s3 get cm s3 -o json | jq -r '.data."s3-url"')
    s3_region=$(kubectl -n s3 get cm s3 -o json | jq -r '.data."s3-region"')
    s3_bucket=$(kubectl -n s3 get cm s3 -o json | jq -r '.data."s3-onboarder-bucket"')
    s3_user_key=$(kubectl -n s3 get cm s3 -o json | jq -r '.data."s3-user-key"')

    echo "Onboarding default partners"
    helm -n $NS install partner-onboarder mosip/partner-onboarder \
      --set onboarding.configmaps.s3.s3-host="$s3_url" \
      --set onboarding.configmaps.s3.s3-user-key="$s3_user_key" \
      --set onboarding.configmaps.s3.s3-region="$s3_region" \
      --set onboarding.configmaps.s3.s3-bucket-name="$s3_bucket" \
      --set extraEnvVarsCM[0]=esignet-global \
      --set extraEnvVarsCM[1]=keycloak-env-vars \
      --set extraEnvVarsCM[2]=keycloak-host \
      $ENABLE_INSECURE \
      -f values.yaml \
      --version $CHART_VERSION --wait-for-jobs

    kubectl -n $NS set env --keys=mosip-esignet-misp-key --from secret/esignet-misp-onboarder-key deployment/esignet-config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_
    kubectl -n $NS rollout restart deploy esignet-config-server
    kubectl -n $NS get deploy esignet-config-server -o name | xargs -n1 -t kubectl -n $NS rollout status
    kubectl -n $NS rollout restart deployment esignet
    echo "eSignet MISP License Key updated successfully."
    echo "Reports are moved to S3 under $s3_bucket bucket."
    return 0
  else
    echo "Please ensure that values.yaml is correctly set."
    exit 1
  fi
}

# Set commands for error handling.
set -e
set -o errexit   ## Exit the script if any statement returns a non-true return value
set -o nounset   ## Exit the script if you try to use an uninitialized variable
set -o errtrace  ## Trace ERR through 'time command' and other functions
set -o pipefail  ## Trace ERR through pipes

# Call the function
installing_onboarder
