#!/bin/bash
## Installing captcha validation server.
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=captcha
CHART_VERSION=0.1.0-develop

echo Create $NS namespace
kubectl create ns $NS

function installing_captcha() {
  echo Istio label

  kubectl label ns $NS istio-injection=disabled --overwrite
  helm repo update

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

  echo Installing captcha
  helm -n $NS install captcha mosip/captcha --version $CHART_VERSION --set metrics.serviceMonitor.enabled=$servicemonitorflag --wait

  echo Installed captcha service
  
  # Check if the second environment variable exists

  ENV_VAR_EXISTS=$(kubectl -n captcha get deployment captcha -o jsonpath="{.spec.template.spec.containers[0].env[?(@.name=='MOSIP_CAPTCHA_SECRET_ESIGNET')].name}")

  if [[ -z "$ENV_VAR_EXISTS" ]]; then
      # If the environment variable does not exist, add it
      echo "Environment variable 'MOSIP_CAPTCHA_SECRET_ESIGNET' does not exist. Adding it..."
      kubectl patch deployment -n captcha captcha --type='json' -p='[{"op": "add", "path": "/spec/template/spec/containers/0/env/-", "value": {"name": "MOSIP_CAPTCHA_SECRET_ESIGNET", "valueFrom": {"secretKeyRef": {"name": "esignet-captcha", "key": "esignet-captcha-secret-key"}}}}]'
  else
      # If the environment variable exists, update it
      echo "Environment variable 'MOSIP_CAPTCHA_SECRET_ESIGNET' exists. Updating it..."
      kubectl patch deployment -n captcha captcha --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/env[?(@.name==\"MOSIP_CAPTCHA_SECRET_ESIGNET\")]", "value": {"name": "MOSIP_CAPTCHA_SECRET_ESIGNET", "valueFrom": {"secretKeyRef": {"name": "esignet-captcha", "key": "esignet-captcha-secret-key"}}}}]'
  fi

  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_captcha   # calling second function
