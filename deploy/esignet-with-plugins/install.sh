#!/bin/bash
# Installs esignet-with-plugins helm chart
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=1.5.1-develop
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

  echo Create $NS namespace
  kubectl create ns $NS || true

  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite
 helm repo add mosip https://mosip.github.io/mosip-helm
 helm repo update

  COPY_UTIL=../copy_cm_func.sh
  $COPY_UTIL configmap esignet-softhsm-share softhsm
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
    echo "'flag' was not provided; EXITING;"
    exit 1;
  fi
  ENABLE_INSECURE=''
  if [ "$flag" = "n" ]; then
    ENABLE_INSECURE='--set enable_insecure=true';
  fi

  echo "Please choose the required plugin to proceed with installation"
  echo "1. esignet-mock-plugin"
  echo "2. mosip-identity-plugin"
  echo "3. sunbird-rc-plugin"
  echo "4. custom-plugin"
  read -p "Enter the plugin number: " plugin_no
  echo "$plugin_no" > /tmp/plugin_no.txt

  while true; do
    if [[ "$plugin_no" == "1" ]]; then
      plugin_option="--set pluginNameEnv=esignet-mock-plugin.jar"
      break
    elif [[ "$plugin_no" == "2" ]]; then
      plugin_option="--set pluginNameEnv=mosip-identity-plugin.jar"
      extra_env_vars_additional=""
      declare -A urls
      urls=(
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL"]="http://mosip-file-server.mosip-file-server/mosip-certs/ida-partner.cer"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-AUTH-URL"]="http://ida-auth.ida/idauthentication/v1/kyc-auth/delegated/${mosip.esignet.authenticator.ida.misp-license-key}/"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-EXCHANGE-URL"]="http://ida-auth.ida/idauthentication/v1/kyc-exchange/delegated/${mosip.esignet.authenticator.ida.misp-license-key}/"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND-OTP-URL"]="http://ida-otp.ida/idauthentication/v1/otp/${mosip.esignet.authenticator.ida.misp-license-key}/"
        ["MOSIP_ESIGNET_BINDER_IDA_KEY-BINDING-URL"]="http://ida-auth.ida/idauthentication/v1/identity-key-binding/delegated/${mosip.esignet.authenticator.ida.misp-license-key}/"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_GET-CERTIFICATES-URL"]="http://ida-internal.ida/idauthentication/v1/internal/getAllCertificates"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH-TOKEN-URL"]="http://authmanager.kernel/v1/authmanager/authenticate/clientidsecretkey"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT-MANAGER-URL"]="http://auditmanager.kernel/v1/auditmanager/audits"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_OTP-CHANNELS"]="email,phone"
      )

      ordered_keys=(
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL"
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-AUTH-URL"
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-EXCHANGE-URL"
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND-OTP-URL"
        "MOSIP_ESIGNET_BINDER_IDA_KEY-BINDING-URL"
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_GET-CERTIFICATES-URL"
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH-TOKEN-URL"
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT-MANAGER-URL"
        "MOSIP_ESIGNET_AUTHENTICATOR_IDA_OTP-CHANNELS"
      )

      for key in "${ordered_keys[@]}"; do
        if [[ "$key" == "MOSIP_ESIGNET_AUTHENTICATOR_IDA_OTP-CHANNELS" ]]; then
          read -p "Default channels (${urls[$key]})  Please add required channels to override the default channels: " user_input
        else
          read -p "Default (${urls[$key]}) - Provide custom value (if applicable) to override the default url: " user_input
        fi
        value="${user_input:-${urls[$key]}}"
        extra_env_vars_additional+="  - name: $key"$'\n'"    value: \"$value\""$'\n'
      done
      extra_env_file=$(mktemp)
      cat <<EOF > "$extra_env_file"
extraEnvVarsAdditional: |
$extra_env_vars_additional
EOF
      plugin_option="$plugin_option -f $extra_env_file"
      cat $extra_env_file
      break
    elif [[ "$plugin_no" == "3" ]]; then
      plugin_option="--set pluginNameEnv=sunbird-rc-plugin.jar"
      read -p "Provide the URL for Sunbird registry: " sunbird_registry_url
      extra_env_vars_additional+="  - name: mosip_esignet_sunbird-rc_registry-get-url"$'\n'"    value: \"$sunbird_registry_url\""$'\n'
      extra_env_file=$(mktemp)
      cat <<EOF > "$extra_env_file"
extraEnvVarsAdditional: |
$extra_env_vars_additional
EOF
      plugin_option="$plugin_option -f $extra_env_file"
      cat $extra_env_file
      break
    elif [[ "$plugin_no" == "4" ]]; then
      read -p "Please provide the url for the custom plugin you want to use :  " plugin_url
      read -p "Provide the plugin jar name (with extension eg., test-plugin.jar): " plugin_jar
      plugin_option="--set pluginNameEnv=$plugin_jar --set pluginUrlEnv=$plugin_url"
      break
    else
      echo "Please provide the correct plugin number (1, 2, 3, or 4)."
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
