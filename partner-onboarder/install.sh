#!/bin/bash
# Onboards default partners 
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

echo "Do you have public domain & valid SSL? (Y/n) "
echo "Y: if you have public domain & valid ssl certificate"
echo "n: If you don't have a public domain and a valid SSL certificate. Note: It is recommended to use this option only in development environments."
read -p "" flag

if [ -z "$flag" ]; then
  echo "'flag' was provided; EXITING;"
  exit 1;
fi
ENABLE_INSECURE=''
if [ "$flag" = "n" ]; then
  ENABLE_INSECURE='--set onboarding.configmaps.onboarding.ENABLE_INSECURE=true';
fi

NS=esignet
CHART_VERSION=12.0.1-B3

echo Create $NS namespace
kubectl create ns $NS

function installing_onboarder() {

  read -p "Is values.yaml for onboarder chart set correctly as part of Pre-requisites?(Y/n) " yn;
  if [ $yn = "Y" ]; then
    echo Istio label
    kubectl label ns $NS istio-injection=disabled --overwrite
    helm repo update

    echo Copy configmaps
    kubectl -n $NS --ignore-not-found=true delete cm s3
    sed -i 's/\r$//' copy_cm.sh
    ./copy_cm.sh
    kubectl -n $NS delete cm --ignore-not-found=true onboarding

    echo Copy secrets
    sed -i 's/\r$//' copy_secrets.sh
    ./copy_secrets.sh

    echo Onboarding default partners
    helm -n $NS install esignet-resident-oidc-partner-onboarder mosip/partner-onboarder \
    --set onboarding.configmaps.s3.s3-host='http://minio.minio:9000' \
    --set onboarding.configmaps.s3.s3-user-key='admin' \
    --set onboarding.configmaps.s3.s3-region='' \
    $ENABLE_INSECURE \
    -f values.yaml \
    --version $CHART_VERSION

    echo Reports are moved to S3 under onboarder bucket

    read -p "The below script copies esignet misp license key to secrets, Please reply yes only if esignet partner is onboarded (Y/n) " yn;
    if [ $yn = "Y" ]; then
        MISPKEY=$(bash misp_key.sh)
        echo "MISP License key is: $MISPKEY"

        echo Setting up onboarder-keys secrets
        kubectl -n $NS create secret generic onboarder-keys --from-literal=mosip-esignet-misp-key=$MISPKEY --dry-run=client -o yaml | kubectl apply -f -

        ./copy_cm_func.sh secret onboarder-keys esignet config-server

        kubectl -n config-server set env --keys=mosip-esignet-misp-key --from secret/onboarder-keys deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_

        kubectl -n config-server get deploy -o name |  xargs -n1 -t  kubectl -n config-server rollout status
        echo E-signet MISP License Key successfully copied
    fi

    return 0
  fi
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_onboarder   # calling function
