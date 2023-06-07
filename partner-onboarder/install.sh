#!/bin/bash
# Onboards default partners 
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

echo "Do you have public domain & valid SSL? (Y/n) "
echo "Y: if you have public domain & valid ssl certificate"
echo "n: if you don't have public domain & valid ssl certificate"
read -p "" flag

if [ -z "$flag" ]; then
  echo "'flag' was provided; EXITING;"
  exit 1;
fi
ENABLE_INSECURE=''
if [ "$flag" = "n" ]; then
  ENABLE_INSECURE='--set onboarding.enableInsecure=true';
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

    echo Copy secrets
    sed -i 's/\r$//' copy_secrets.sh
    ./copy_secrets.sh

    read -p "Provide onboarder bucket name : " s3_bucket
    if [[ -z $s3_bucket ]]; then
      echo "s3_bucket not provided; EXITING;";
      exit 1;
    fi
    if [[ $s3_bucket == *[' !@#$%^&*()+']* ]]; then
      echo "s3_bucket should not contain spaces / any special character; EXITING";
      exit 1;
    fi
    read -p "Provide onboarder s3 bucket region : " s3_region
    if [[ $s3_region == *[' !@#$%^&*()+']* ]]; then
      echo "s3_region should not contain spaces / any special character; EXITING";
      exit 1;
    fi

    read -p "Provide S3 URL : " s3_url
    if [[ -z $s3_url ]]; then
      echo "s3_url not provided; EXITING;"
      exit 1;
    fi

    s3_user_key=$( kubectl -n s3 get cm s3 -o json | jq -r '.data."s3-user-key"' )

    echo Onboarding default partners
    helm -n $NS install esignet-resident-oidc-partner-onboarder mosip/partner-onboarder \
    --set onboarding.configmaps.s3.s3-host="$s3_url" \
    --set onboarding.configmaps.s3.s3-user-key="$s3_user_key" \
    --set onboarding.configmaps.s3.s3-region="$s3_region" \
    --set onboarding.configmaps.s3.s3-bucket-name="$s3_bucket" \
    $ENABLE_INSECURE \
    -f values.yaml \
    --version $CHART_VERSION

    echo Reports are moved to S3 under onboarder bucket
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
