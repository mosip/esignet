#!/bin/bash
# Installs apitestrig
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=0.0.1-develop
COPY_UTIL=../copy_cm_func.sh

echo Create $NS namespace
kubectl create ns $NS

function installing_apitestrig() {
  echo Istio label
  kubectl label ns $NS istio-injection=disabled --overwrite
  helm repo update

  echo Copy Configmaps
  $COPY_UTIL configmap global default $NS
  $COPY_UTIL configmap keycloak-host keycloak $NS
  $COPY_UTIL configmap artifactory-share artifactory $NS
  $COPY_UTIL configmap config-server-share config-server $NS

  echo echo Copy Secrtes
  $COPY_UTIL secret keycloak-client-secrets keycloak $NS
  $COPY_UTIL secret s3 s3 $NS
  $COPY_UTIL secret postgres-postgresql postgres $NS

  echo "Delete s3, db, & apitestrig configmap if exists"
  kubectl -n $NS delete --ignore-not-found=true configmap s3
  kubectl -n $NS delete --ignore-not-found=true configmap db
  kubectl -n $NS delete --ignore-not-found=true configmap apitestrig

  DB_HOST=$( kubectl -n default get cm global -o json  |jq -r '.data."mosip-api-internal-host"' )
  API_INTERNAL_HOST=$( kubectl -n default get cm global -o json  |jq -r '.data."mosip-api-internal-host"' )
  ENV_USER=$( kubectl -n default get cm global -o json |jq -r '.data."mosip-api-internal-host"' | awk -F '.' '/api-internal/{print $1"."$2}')

  read -p "Please enter the time(hr) to run the cronjob every day (time: 0-23) : " time
  if [ -z "$time" ]; then
     echo "ERROT: Time cannot be empty; EXITING;";
     exit 1;
  fi
  if ! [ $time -eq $time ] 2>/dev/null; then
     echo "ERROR: Time $time is not a number; EXITING;";
     exit 1;
  fi
  if [ $time -gt 23 ] || [ $time -lt 0 ] ; then
     echo "ERROR: Time should be in range ( 0-23 ); EXITING;";
     exit 1;
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
    ENABLE_INSECURE='--set enable_insecure=true';
  fi

  read -p "Please provide the retention days to remove old reports ( Default: 3 )" reportExpirationInDays

  if [[ -z $reportExpirationInDays ]]; then
    reportExpirationInDays=3
  fi
  if ! [[ $reportExpirationInDays =~ ^[0-9]+$ ]]; then
    echo "The variable \"reportExpirationInDays\" should contain only number; EXITING";
    exit 1;
  fi

  read -p "Please provide slack webhook URL to notify server end issues on your slack channel : " slackWebhookUrl

  if [ -z $slackWebhookUrl ]; then
    echo "slack webhook URL not provided; EXITING;"
    exit 1;
  fi

 valid_inputs=("yes" "no")
 eSignetDeployed=""

 while [[ ! " ${valid_inputs[@]} " =~ " ${eSignetDeployed} " ]]; do
     read -p "Is the eSignet service deployed? (yes/no): " eSignetDeployed
     eSignetDeployed=${eSignetDeployed,,}  # Convert input to lowercase
 done

 if [[ $eSignetDeployed == "yes" ]]; then
     echo "eSignet service is deployed. Proceeding with installation..."
 else
     echo "eSignet service is not deployed. hence will be skipping esignet related test-cases..."
 fi

  echo Installing esignet apitestrig
  helm -n $NS install esignet-apitestrig mosip/apitestrig \
  --set crontime="0 $time * * *" \
  -f values.yaml  \
  --version $CHART_VERSION \
  --set apitestrig.configmaps.s3.s3-host='http://minio.minio:9000' \
  --set apitestrig.configmaps.s3.s3-user-key='admin' \
  --set apitestrig.configmaps.s3.s3-region='' \
  --set apitestrig.configmaps.db.db-server="$DB_HOST" \
  --set apitestrig.configmaps.db.db-su-user="postgres" \
  --set apitestrig.configmaps.db.db-port="5432" \
  --set apitestrig.configmaps.apitestrig.ENV_USER="$ENV_USER" \
  --set apitestrig.configmaps.apitestrig.ENV_ENDPOINT="https://$API_INTERNAL_HOST" \
  --set apitestrig.configmaps.apitestrig.ENV_TESTLEVEL="smokeAndRegression" \
  --set apitestrig.configmaps.apitestrig.reportExpirationInDays="$reportExpirationInDays" \
  --set apitestrig.configmaps.apitestrig.slack-webhook-url="$slackWebhookUrl" \
  --set apitestrig.configmaps.apitestrig.eSignetDeployed="$eSignetDeployed" \
  --set apitestrig.configmaps.apitestrig.NS="$NS" \
  $ENABLE_INSECURE

  echo Installed esignet apitestrig.
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_apitestrig   # calling function