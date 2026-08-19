#!/bin/bash
# Installs esignet helm chart
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

echo Create $NS namespace
kubectl create ns $NS

function installing_esignet() {

  while true; do
      read -p "Do you want to continue installing esignet services? (y/n): " ans
      if [ "$ans" = "Y" ] || [ "$ans" = "y" ]; then
          break
      elif [ "$ans" = "N" ] || [ "$ans" = "n" ]; then
          exit 1
      else
          echo "Please provide a correct option (Y or N)"
      fi
  done

  NS=esignet
  CHART_VERSION=2.0.0-develop

  ESIGNET_HOST=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-esignet-host})

  echo Create $NS namespace
  kubectl create ns $NS || true

  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite
  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  COPY_UTIL=../copy_cm_func.sh
  # SoftHSM may be installed in the softhsm namespace (collab) or esignet
  # namespace (install-prereq.sh). Copy into $NS when the source is elsewhere.
  if kubectl -n softhsm get configmap esignet-softhsm-share >/dev/null 2>&1; then
    $COPY_UTIL configmap esignet-softhsm-share softhsm $NS
    $COPY_UTIL secret esignet-softhsm softhsm $NS
  elif kubectl -n $NS get configmap esignet-softhsm-share >/dev/null 2>&1; then
    echo "Using esignet-softhsm-share already present in $NS"
  else
    echo "Warning: esignet-softhsm-share not found. PKCS11 needs libpkcs11-proxy.so from the HSM client zip (baked into the image, or set hsm_client_zip_url_env)."
  fi
  $COPY_UTIL configmap postgres-config postgres $NS
  $COPY_UTIL configmap redis-config redis $NS
  $COPY_UTIL secret redis redis $NS

  while true; do
    read -p "Is Prometheus Service Monitor Operator deployed in the k8s cluster? (y/n): " response
    if [[ "$response" == "y" || "$response" == "Y" ]]; then
      servicemonitorflag=true
      break
    elif [[ "$response" == "n" || "$response" == "N" ]]; then
      servicemonitorflag=false
      break
    else
      echo "Not a correct response. Please respond with y (yes) or n (no)."
    fi
  done

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

  while true; do
    read -p "For PKCS12, opt 'y' to enable volume (y/n) [ default: n ]: " enable_volume
    enable_volume=${enable_volume:-n}

    if [[ "$enable_volume" == "y" || "$enable_volume" == "Y" ]]; then
      enable_volume=true
      break
    elif [[ "$enable_volume" == "n" || "$enable_volume" == "N" ]]; then
      enable_volume=false
      break
    else
      echo "Invalid input. Please enter 'y' or 'n'."
    fi
  done

  ESIGNET_HELM_ARGS=''
  if [[ $enable_volume == 'true' ]]; then

    default_volume_size=100M
    read -p "Provide the size for volume [ default : 100M ]" volume_size
    volume_size=${volume_size:-$default_volume_size}

    default_volume_mount_path='/home/mosip/config/'
    read -p "Provide the mount path for volume [ default : '/home/mosip/config/' ] : " volume_mount_path
    volume_mount_path=${volume_mount_path:-$default_volume_mount_path}

    PVC_CLAIM_NAME='esignet-pkcs12'
    ESIGNET_HELM_ARGS="--set persistence.enabled=true  \
            --set volumePermissions.enabled=true \
            --set persistence.size=$volume_size \
            --set persistence.mountDir=\"$volume_mount_path\" \
            --set persistence.pvc_claim_name=\"$PVC_CLAIM_NAME\"  \
            --set extraEnvVarsCM={'esignet-global','config-server-share','artifactory-share'} \
            --set extraEnvVarsAdditional.KEYMANAGER_KEYSTORE_TYPE=PKCS12 \
            --set extraEnvVarsAdditional.KEYMANAGER_PKCS12_FILE_PATH=${volume_mount_path}/esignet.p12 \
            --set extraEnvVarsAdditional.KEYMANAGER_PKCS12_PASSWORD=localtest \
            --set extraEnvVarsAdditional.KEYMANAGER_PKCS12_ALLOW_INSECURE_SOFTWARE_KEYSTORE=true \
            "
  fi
  echo "ESIGNET HELM ARGS $ESIGNET_HELM_ARGS"

  read -p "Provide the URL to download the plugins zip " plugin_url
  read -p "Provide the plugin jar name (with extension eg., test-plugin.jar) " plugin_jar
  plugin_option="--set pluginNameEnv=$plugin_jar --set pluginUrlEnv=$plugin_url"

  echo Installing esignet
  helm -n $NS install esignet mosip/esignet --version $CHART_VERSION \
  $ESIGNET_HELM_ARGS \
  $ENABLE_INSECURE $plugin_option \
  --set metrics.serviceMonitor.enabled=$servicemonitorflag -f values.yaml --wait

  kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

  echo Installed esignet service
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_esignet   # calling function
