#!/bin/bash
# Installs esignet MISP onboarder helm
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

echo "Do you have public domain & valid SSL? (Y/n) "
echo "Y: if you have public domain & valid ssl certificate"
echo "n: if you don't have public domain & valid ssl certificate"
read -p "" flag

if [ -z "$flag" ]; then
  echo "'flag' was not provided; EXITING;"
  exit 1;
fi
ENABLE_INSECURE=''
if [ "$flag" = "n" ]; then
  ENABLE_INSECURE='--set onboarding.configmaps.onboarding.ENABLE_INSECURE=true';
fi

NS=esignet
CHART_VERSION=0.0.1-develop

echo Create $NS namespace
kubectl create ns $NS || true

function installing_onboarder() {

  read -p "Is values.yaml for onboarder chart set correctly as part of pre-requisites? (Y/n) : " yn;
  if [[ $yn = "Y" ]] || [[ $yn = "y" ]] ; then
    NFS_OPTION=''
    S3_OPTION=''
    config_complete=false  # flag to check if S3 or NFS is configured
    while [ "$config_complete" = false ]; do
      read -p "Do you have S3 details for storing Onboarder reports? (Y/n) : " ans
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
        read -p "Please provide S3 bucket: " s3_bucket
        if [[ $s3_bucket == *[' !@#$%^&*()+']* ]]; then
          echo "S3 bucket should not contain spaces or special characters; EXITING;"
          exit 1;
        fi
        read -p "Please provide S3 access key: " s3_user_key
        if [[ -z $s3_user_key ]]; then
          echo "S3 access key not provided; EXITING;"
          exit 1;
        fi
        read -p "Please provide S3 secret key: " s3_secret_key
        if [[ -z $s3_secret_key ]]; then
          echo "S3 secret key not provided; EXITING;"
          exit 1;
        fi
        S3_OPTION="--set onboarding.configmaps.s3.s3-host=$s3_host --set onboarding.configmaps.s3.s3-user-key=$s3_user_key --set onboarding.configmaps.s3.s3-region=$s3_region --set onboarding.configmaps.s3.s3-bucket-name=$s3_bucket --set onboarding.secrets.s3.s3-user-secret=$s3_secret_key"
        push_reports_to_s3=true
        config_complete=true
      elif [[ "$ans" == "n" || "$ans" == "N" ]]; then
        push_reports_to_s3=false
        read -p "Since S3 details are not available, do you want to use NFS directory mount for storing reports? (y/n) : " answer
        if [[ $answer == "Y" ]] || [[ $answer == "y" ]]; then
          read -p "Please provide NFS Server IP: " nfs_server
          if [[ -z $nfs_server ]]; then
            echo "NFS server not provided; EXITING."
            exit 1;
          fi
          read -p "Please provide NFS directory to store reports from NFS server (e.g. /srv/nfs/mosip/<sandbox>/onboarder/), make sure permission is 777 for the folder: " nfs_path
          if [[ -z $nfs_path ]]; then
            echo "NFS Path not provided; EXITING."
            exit 1;
          fi
          NFS_OPTION="--set onboarding.volumes.reports.nfs.server=$nfs_server --set onboarding.volumes.reports.nfs.path=$nfs_path"
          config_complete=true
        else
          echo "Please rerun the script with either S3 or NFS server details."
          exit 1;
        fi
      else
        echo "Invalid input. Please respond with Y (yes) or N (no)."
      fi
    done

    kubectl -n $NS delete configmap esignet-onboarder-config --ignore-not-found=true
    kubectl -n $NS delete secret generic esignet-onboarder-secrets --ignore-not-found=true
    while true; do
      read -p "Are you using an external Keycloak instance? (Y/n): " use_external_keycloak
      if [[ "$use_external_keycloak" == "Y" || "$use_external_keycloak" == "y" ]]; then
        # External Keycloak config starts here
        read -p "Please provide KEYCLOAK_EXTERNAL_URL: " KEYCLOAK_EXTERNAL_URL
        if [ -z "$KEYCLOAK_EXTERNAL_URL" ]; then
          echo "ERROR: KEYCLOAK EXTERNAL URL not provided; EXITING;"
          exit 1
        fi

        read -p "Please provide KEYCLOAK ADMIN USER: " KEYCLOAK_ADMIN_USER
        if [ -z "$KEYCLOAK_ADMIN_USER" ]; then
          echo "ERROR: KEYCLOAK ADMIN USER not provided; EXITING;"
          exit 1
        fi

        echo "Please provide KEYCLOAK ADMIN PASSWORD"
        read -s KEYCLOAK_ADMIN_PASSWORD
        if [ -z "$KEYCLOAK_ADMIN_PASSWORD" ]; then
          echo "ERROR: KEYCLOAK ADMIN PASSWORD not provided; EXITING;"
          exit 1
        fi

        read -p "Please provide PMS DOMAIN (ex: api-internal.sandbox.mosip.net): " PMS_DOMAIN
        if [ -z "$PMS_DOMAIN" ]; then
          echo "ERROR: PMS DOMAIN not provided; EXITING;"
          exit 1
        fi

        echo "Please provide PMS CLIENT SECRET"
        read -s PMS_CLIENT_SECRET
        if [ -z "$PMS_CLIENT_SECRET" ]; then
          echo "ERROR: PMS CLIENT SECRET not provided; EXITING;"
          exit 1
        fi

        kubectl -n $NS create configmap esignet-onboarder-config \
          --from-literal=keycloak-external-url="$KEYCLOAK_EXTERNAL_URL" \
          --from-literal=KEYCLOAK_ADMIN_USER="$KEYCLOAK_ADMIN_USER" \
          --from-literal=mosip-api-internal-host="$PMS_DOMAIN" \
          --dry-run=client -o yaml | kubectl apply -f -

        kubectl -n $NS create secret generic esignet-onboarder-secrets \
          --from-literal=admin-password="$KEYCLOAK_ADMIN_PASSWORD" \
          --from-literal=mosip_pms_client_secret="$PMS_CLIENT_SECRET" \
          --dry-run=client -o yaml | kubectl apply -f -

        KEYCLOAK_ARGS="--set extraEnvVarsCM={esignet-onboarder-config} --set extraEnvVarsSecret={esignet-onboarder-secrets}"
        break
      elif [[ "$use_external_keycloak" == "N" || "$use_external_keycloak" == "n" ]]; then
        KEYCLOAK_ARGS="--set extraEnvVarsCM={esignet-global,keycloak-env-vars,keycloak-host} --set extraEnvVarsSecret={keycloak,keycloak-client-secrets}"
        break
      else
        echo "Invalid option. Please enter 'Y' or 'n'."
      fi
    done

   echo "Istio label"
   kubectl label ns $NS istio-injection=disabled --overwrite
   helm repo update

   if ! kubectl -n $NS get configmap esignet-onboarder-config >/dev/null 2>&1 || \
      ! kubectl -n $NS get secret esignet-onboarder-secrets >/dev/null 2>&1; then
     echo "Copying configmaps and secrets..."
     COPY_UTIL=../deploy/copy_cm_func.sh
     $COPY_UTIL configmap keycloak-env-vars keycloak $NS
     $COPY_UTIL configmap keycloak-host keycloak $NS

     $COPY_UTIL secret keycloak keycloak $NS
     $COPY_UTIL secret keycloak-client-secrets keycloak $NS
  fi


    echo "Onboarding Esignet MISIP partner client"
    helm -n $NS install esignet-misp-onboarder mosip/partner-onboarder \
      $NFS_OPTION \
      $S3_OPTION \
      --set onboarding.variables.push_reports_to_s3=$push_reports_to_s3 \
      $ENABLE_INSECURE \
      -f values.yaml \
      $KEYCLOAK_ARGS \
      --version $CHART_VERSION \
      --wait --wait-for-jobs
    echo "Partner onboarder executed and reports are moved to S3 or NFS please check the same to make sure partner was onboarded sucessfully."
    kubectl rollout restart deployment -n esignet esignet
    echo eSignet MISP License Key updated successfully to eSignet.
    return 0
  fi
}

# set commands for error handling.
set -e
set -o errexit   # exit the script if any statement returns a non-true return value
set -o nounset   # exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_onboarder   # calling function
