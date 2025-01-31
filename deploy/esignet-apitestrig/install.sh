#!/bin/bash
# Installs apitestrig
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=1.5.0
COPY_UTIL=../copy_cm_func.sh

echo Create $NS namespace
kubectl create ns $NS

function installing_apitestrig() {
  echo Istio label
  kubectl label ns $NS istio-injection=disabled --overwrite
  helm repo update

  echo Copy Configmaps
  $COPY_UTIL configmap keycloak-host keycloak $NS
  $COPY_UTIL configmap artifactory-share artifactory $NS
  $COPY_UTIL configmap config-server-share config-server $NS

  echo echo Copy Secrtes
  $COPY_UTIL secret keycloak-client-secrets keycloak $NS
  $COPY_UTIL secret postgres-postgresql postgres $NS

  echo "Delete s3, db, & apitestrig configmap if exists"
  kubectl -n $NS delete --ignore-not-found=true configmap s3
  kubectl -n $NS delete --ignore-not-found=true configmap db
  kubectl -n $NS delete --ignore-not-found=true configmap apitestrig

  DB_HOST=$( kubectl -n esignet get cm esignet-global -o json  |jq -r '.data."mosip-api-internal-host"' )
  API_INTERNAL_HOST=$( kubectl -n esignet get cm esignet-global -o json  |jq -r '.data."mosip-api-internal-host"' )
  ENV_USER=$( kubectl -n esignet get cm esignet-global -o json |jq -r '.data."mosip-api-internal-host"' | awk -F '.' '/api-internal/{print $1"."$2}')

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
  read -p "Is values.yaml for apitestrig chart set correctly as part of pre-requisites? (Y/n) : " yn;
  if [[ $yn = "Y" ]] || [[ $yn = "y" ]] ; then
    NFS_OPTION=''
    S3_OPTION=''
    config_complete=false  # flag to check if S3 or NFS is configured
    while [ "$config_complete" = false ]; do
      read -p "Do you have S3 details for storing apitestrig reports? (Y/n) : " ans
      if [[ "$ans" == "y" || "$ans" == "Y" ]]; then
        read -p "Please provide S3 host: " s3_host
        if [[ -z $s3_host ]]; then
          echo "S3 host not provided; EXITING;"
          exit 1;
        fi
        read -p "Please provide S3 region: " s3_region
        if [[ $s3_region == *[' !@#$%^&*()+']* ]]; then
          echo "S3 region should not contain spaces or special characters; EXITING;"
          exit 1;
        fi

        read -p "Please provide S3 access key: " s3_user_key
        if [[ -z $s3_user_key ]]; then
          echo "S3 access key not provided; EXITING;"
          exit 1;
        fi
        S3_OPTION="--set apitestrig.configmaps.s3.s3-host=$s3_host --set apitestrig.configmaps.s3.s3-user-key=$s3_user_key --set apitestrig.configmaps.s3.s3-region=$s3_region"
        push_reports_to_s3="yes"
        config_complete=true
      elif [[ "$ans" == "n" || "$ans" == "N" ]]; then
        push_reports_to_s3="no"
        read -p "Since S3 details are not available, do you want to use NFS directory mount for storing reports? (y/n) : " answer
        if [[ $answer == "Y" ]] || [[ $answer == "y" ]]; then
          read -p "Please provide NFS Server IP: " nfs_server
          if [[ -z $nfs_server ]]; then
            echo "NFS server not provided; EXITING."
            exit 1;
          fi
          read -p "Please provide NFS directory to store reports from NFS server (e.g. /srv/nfs/mosip/<sandbox>/apitestrig/), make sure permission is 777 for the folder: " nfs_path
          if [[ -z $nfs_path ]]; then
            echo "NFS Path not provided; EXITING."
            exit 1;
          fi
          NFS_OPTION="--set apitestrig.volumes.reports.nfs.server=$nfs_server --set apitestrig.volumes.reports.nfs.path=$nfs_path"
          config_complete=true
        else
          echo "Please rerun the script with either S3 or NFS server details."
          exit 1;
        fi
      else
        echo "Invalid input. Please respond with Y (yes) or N (no)."
      fi
    done
    echo Installing esignet apitestrig
    helm -n $NS install esignet-apitestrig mosip/apitestrig \
    --set crontime="0 $time * * *" \
    -f values.yaml  \
    --version $CHART_VERSION \
    $NFS_OPTION \
    $S3_OPTION \
    --set apitestrig.variables.push_reports_to_s3=$push_reports_to_s3 \
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
  fi  
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_apitestrig   # calling function
