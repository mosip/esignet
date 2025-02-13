#!/bin/bash
# Installs esignet-with-plugins helm chart
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=1.5.0
echo Create $NS namespace
kubectl create ns $NS

function installing_esignet_with_plugins() {

  while true; do
      read -p "Do you want to continue installing esignet-with-plugins services? (y/n): " ans
      if [ "$ans" = "Y" ] || [ "$ans" = "y" ]; then
          break
      elif [ "$ans" = "N" ] || [ "$ans" = "n" ]; then
          exit 1
      else
          echo "Please provide a correct option (Y or N)"
      fi
  done

  ESIGNET_HOST=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-esignet-host})

  echo Create $NS namespace
  kubectl create ns $NS || true

  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite
  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  COPY_UTIL=../copy_cm_func.sh
  $COPY_UTIL configmap esignet-softhsm-share softhsm $NS
  $COPY_UTIL configmap postgres-config postgres $NS
  $COPY_UTIL configmap redis-config redis $NS
  $COPY_UTIL secret esignet-softhsm softhsm $NS
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
    read -p "Do you want to use the default plugins? (y/n): " ans
    if [[ "$ans" == "y" || "$ans" == "Y" ]]; then
      echo "Default plugins are listed below, please provide the correct plugin number."
      echo "1. esignet-mock-plugin.jar"
      echo "2. mosip-identity-plugin.jar"
      read -p "Enter the plugin number: " plugin_no
        while true; do
          if [[ "$plugin_no" == "1" ]]; then
            plugin_option="--set pluginNameEnv=esignet-mock-plugin.jar"
            break
          elif [[ "$plugin_no" == "2" ]]; then
            plugin_option="--set pluginNameEnv=mosip-identity-plugin.jar"
            break
          else
            echo "please provide the correct plugin number (1 or 2)."
          fi
        done
      break
    elif [[ "$ans" == "n" || "$ans" == "N" ]]; then
      read -p "Provide the URL to download the plugins zip " plugin_url
      read -p "Provide the plugin jar name (with extension eg., test-plugin.jar) " plugin_jar
      plugin_option="--set pluginNameEnv=$plugin_jar --set pluginUrlEnv=$plugin_url"
      break
    else
      echo " Invalid response. Please respond with y (yes) or n (no)."
    fi
  done

  echo Installing esignet-with-plugins
  helm -n $NS install esignet mosip/esignet --version $CHART_VERSION \
  $ENABLE_INSECURE $plugin_option \
  --set metrics.serviceMonitor.enabled=$servicemonitorflag -f values.yaml --wait

  kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

  echo Installed esignet-with-plugins service
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_esignet_with_plugins   # calling function
