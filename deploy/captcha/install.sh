#!/bin/bash
## Combined Script: Installing Captcha Validation Server and Initializing Prerequisites
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

ROOT_DIR=`pwd`
NS=captcha
CHART_VERSION=1.5.0

function installing_captcha() {
  while true; do
      read -p "Do you want to continue installing captcha validation service? (y/n): " ans
      if [ "$ans" = "Y" ] || [ "$ans" = "y" ]; then
          break
      elif [ "$ans" = "N" ] || [ "$ans" = "n" ]; then
          exit 1
      else
          echo "Please provide a correct option (Y or N)"
      fi
  done

  echo "Creating $NS namespace"
  kubectl create ns $NS || true

  echo "Applying Istio label to namespace"
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

  echo "Installing captcha"
  helm -n $NS install captcha mosip/captcha --version $CHART_VERSION --set metrics.serviceMonitor.enabled=$servicemonitorflag --wait

  echo "Installed captcha service"

  # Set up Captcha secrets for eSignet
  while true; do
    read -p "Do you want to continue configuring Captcha secrets for esignet? (y/n): " ans
    if [[ "$ans" == "Y" || "$ans" == "y" ]]; then
      ESIGNET_HOST=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-esignet-host})
      echo "Please create captcha site and secret key for esignet domain: $ESIGNET_HOST"

      echo "Please enter the reCAPTCHA admin site key for domain $ESIGNET_HOST:"
      read ESITE_KEY
      echo "Please enter the reCAPTCHA admin secret key for domain $ESIGNET_HOST:"
      read ESECRET_KEY

      echo "Setting up Captcha secrets"
      kubectl -n esignet create secret generic esignet-captcha --from-literal=esignet-captcha-site-key=$ESITE_KEY --from-literal=esignet-captcha-secret-key=$ESECRET_KEY --dry-run=client -o yaml | kubectl apply -f -
      echo "Captcha secrets for esignet configured successfully"

      ../copy_cm_func.sh secret esignet-captcha esignet $NS

      # Update or add environment variable
      ENV_VAR_EXISTS=$(kubectl -n $NS get deployment captcha -o jsonpath="{.spec.template.spec.containers[0].env[?(@.name=='MOSIP_CAPTCHA_SECRET_ESIGNET')].name}")
      if [[ -z "$ENV_VAR_EXISTS" ]]; then
          echo "Environment variable 'MOSIP_CAPTCHA_SECRET_ESIGNET' does not exist. Adding it..."
          kubectl patch deployment -n $NS captcha --type='json' -p='[{"op": "add", "path": "/spec/template/spec/containers/0/env/-", "value": {"name": "MOSIP_CAPTCHA_SECRET_ESIGNET", "valueFrom": {"secretKeyRef": {"name": "esignet-captcha", "key": "esignet-captcha-secret-key"}}}}]'
      else
          echo "Environment variable 'MOSIP_CAPTCHA_SECRET_ESIGNET' exists. Updating it..."
          kubectl patch deployment -n $NS captcha --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/env[?(@.name==\"MOSIP_CAPTCHA_SECRET_ESIGNET\")]", "value": {"name": "MOSIP_CAPTCHA_SECRET_ESIGNET", "valueFrom": {"secretKeyRef": {"name": "esignet-captcha", "key": "esignet-captcha-secret-key"}}}}]'
      fi

      break
    elif [[ "$ans" == "N" || "$ans" == "n" ]]; then
      echo "Skipping Captcha secrets configuration."
      break
    else
      echo "Please provide a correct option (Y or N)"
    fi
  done
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
installing_captcha   # calling second function
