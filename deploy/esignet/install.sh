#!/bin/bash
# Installs esignet helm chart
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
ESIGNET_SERVICE_NAME=esignet
CHART_VERSION=2.0.0-develop
SOFTHSM_SERVICE_NAME=esignet-softhsm
SOFTHSM_CM_NAME=${SOFTHSM_CM_NAME:-${SOFTHSM_SERVICE_NAME}-share}
SOFTHSM_CHART_VERSION=12.0.1
echo Create $NS namespace
kubectl create ns $NS || true

function installing_softhsm() {
  SOFTHSM_VALUES_PATH=${SOFTHSM_VALUES_PATH:-../softhsm/softhsm-values.yaml}

  if [ ! -f "$SOFTHSM_VALUES_PATH" ]; then
    echo "ERROR: SoftHSM values file not found at '$SOFTHSM_VALUES_PATH'."
    echo "Set SOFTHSM_VALUES_PATH to the correct location and re-run."
    return 1
  fi

  echo "Istio label (softhsm namespace)"
  kubectl label ns $NS istio-injection=enabled --overwrite
  helm repo update

  echo "Installing Softhsm for esignet"
  if helm status "$SOFTHSM_SERVICE_NAME" -n "$NS" &>/dev/null; then
    echo "SoftHSM release '$SOFTHSM_SERVICE_NAME' already exists in namespace '$NS' - upgrading in place with the current softhsm-values.yaml."
  else
    echo "No existing SoftHSM release found - installing fresh."
  fi
  helm -n "$NS" upgrade --install $SOFTHSM_SERVICE_NAME mosip/softhsm -f "$SOFTHSM_VALUES_PATH" --version "$SOFTHSM_CHART_VERSION" --wait
  echo "Installed Softhsm for esignet"

  return 0
}

function prompt_hsm_choice() {
  echo ""
  echo "Which HSM deployment do you want to use?"
  echo "  1) SoftHSM (software-based, installed via Helm)"
  echo "  2) Hardware HSM"
  echo ""
  read -rp "Enter your choice [1-2]: " HSM_CHOICE

  case "$HSM_CHOICE" in
    1)
      installing_softhsm
      ;;
    2)
      echo ""
      echo "Hardware HSM setup is not available yet. Please check back later or contact the platform team for assistance."
      echo "Exiting without making any changes."
      exit 0
      ;;
    *)
      echo "Invalid choice: '$HSM_CHOICE'. Please enter 1 or 2."
      prompt_hsm_choice
      ;;
  esac
}

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

  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite
  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  COPY_UTIL=../copy_cm_func.sh
  $COPY_UTIL configmap postgres-config postgres $NS
  $COPY_UTIL configmap redis-config redis $NS
  $COPY_UTIL configmap keycloak-host keycloak $NS
  $COPY_UTIL secret redis redis $NS
  $COPY_UTIL secret keycloak-client-secrets keycloak $NS

  MOSIP_ESIGNET_HOST_DOMAIN=$(kubectl -n $NS get cm esignet-global -o jsonpath={.data.mosip-esignet-host})
  if [[ -z "$MOSIP_ESIGNET_HOST_DOMAIN" ]]; then
    echo "ERROR: could not read 'mosip-esignet-host' from the 'esignet-global' configmap in namespace '$NS'."
    exit 1
  fi
  MOSIP_ESIGNET_HOST="https://$MOSIP_ESIGNET_HOST_DOMAIN"
  MOSIP_ESIGNET_BASE_URL="https://$MOSIP_ESIGNET_HOST_DOMAIN/v1/esignet"

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

  ESIGNET_HELM_ARGS=''
  EXTRA_ENV_VARS_CM_YAML=''
  keystore_env_vars=""
  extra_env_vars_additional=""
  plugin_option=""
  plugin_name=""

  # ---------------------------------------------------------------------
  # Plugin selection. The keystore type is now derived from this choice:
  #   mock    -> PKCS12
  #   mosip   -> PKCS11
  #   sunbird -> PKCS11
  # ---------------------------------------------------------------------
  echo "Please choose the required plugin to proceed with installation"
  echo "1. mock"
  echo "2. mosip"
  echo "3. sunbird"
  read -p "Enter the plugin number: " plugin_no
  PLUGIN_NO_FILE=${PLUGIN_NO_FILE:-$(mktemp)}
    echo "$plugin_no" > "$PLUGIN_NO_FILE"

  while true; do
    if [[ "$plugin_no" == "1" ]]; then
      plugin_name="mock"
      read -p "Is the mock plugin deployed in the default namespace? (y/n): " mock_default_ns
      if [[ "$mock_default_ns" =~ ^[Yy]$ ]]; then
            mock_domain_url="http://mock-identity-system.mockid"
          else
            read -p "Enter MOSIP_ESIGNET_MOCK_DOMAIN_URL: " mock_domain_url

            if [[ -z "$mock_domain_url" ]]; then
              echo "ERROR: MOSIP_ESIGNET_MOCK_DOMAIN_URL cannot be empty."
              continue
            fi
          fi
          extra_env_vars_additional+="  \"MOSIP_ESIGNET_MOCK_DOMAIN_URL\": \"$mock_domain_url\""$'\n'
          break
      break

    elif [[ "$plugin_no" == "2" ]]; then
      echo "Setting up Esignet MISP license key secret"
      default_misp_key="dummy-mosip-esignet-misp-key"
      read -p "Enter the MISP license key [default: dummy placeholder '$default_misp_key']: " misp_key_value
      misp_key_value=${misp_key_value:-$default_misp_key}
      kubectl -n $NS create secret generic esignet-misp-onboarder-key --from-literal=mosip-esignet-misp-key="$misp_key_value" --dry-run=client -o yaml | kubectl apply -f -
      plugin_name="mosip"
      declare -A urls=(
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL"]="http://mosip-file-server.mosip-file-server/mosip-certs/ida-partner.cer"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-AUTH-URL"]="http://ida-auth.ida/idauthentication/v1/kyc-auth/delegated/\${mosip.esignet.authenticator.ida.misp-license-key}/"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-EXCHANGE-URL"]="http://ida-auth.ida/idauthentication/v1/kyc-exchange/delegated/\${mosip.esignet.authenticator.ida.misp-license-key}/"
        ["MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND-OTP-URL"]="http://ida-otp.ida/idauthentication/v1/otp/\${mosip.esignet.authenticator.ida.misp-license-key}/"
        ["MOSIP_ESIGNET_BINDER_IDA_KEY-BINDING-URL"]="http://ida-auth.ida/idauthentication/v1/identity-key-binding/delegated/\${mosip.esignet.authenticator.ida.misp-license-key}/"
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
        extra_env_vars_additional+="  \"$key\": \"$value\""$'\n'
      done
      # MOSIP_LICENSE_KEY references the existing esignet-misp-onboarder-key secret
      extra_env_vars_additional+="  MOSIP_ESIGNET_MISP_KEY:"$'\n'
      extra_env_vars_additional+="    valueFrom:"$'\n'
      extra_env_vars_additional+="      secretKeyRef:"$'\n'
      extra_env_vars_additional+="        name: esignet-misp-onboarder-key"$'\n'
      extra_env_vars_additional+="        key: mosip-esignet-misp-key"$'\n'
      break

    elif [[ "$plugin_no" == "3" ]]; then
      plugin_name="sunbird"
      read -p "Provide the URL for Sunbird registry: " sunbird_registry_url
      extra_env_vars_additional+="  \"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL\": \"$sunbird_registry_url\""$'\n'
      extra_env_vars_additional+="  \"MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL\": \"$sunbird_registry_url/api/v1/Insurance/search\""$'\n'
      extra_env_vars_additional+="  \"MOSIP_ESIGNET_AUTHENTICATOR_DEFAULT_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD\": \"\${mosip.esignet.authenticator.sunbird-rc.auth-factor.kbi.individual-id-field}\""$'\n'
      extra_env_vars_additional+="  \"MOSIP_ESIGNET_AUTHENTICATOR_DEFAULT_AUTH_FACTOR_KBI_FIELD_DETAILS\": \"\${mosip.esignet.authenticator.sunbird-rc.auth-factor.kbi.field-details}\""$'\n'
      break
    else
      echo "Please provide the correct plugin number (1, 2, or 3)."
      read -p "Enter the plugin number: " plugin_no
    fi
  done

  # ---------------------------------------------------------------------
  # Keystore configuration, determined by the selected plugin:
  #   mock               -> PKCS12 (no HSM, extraEnvVarsCM forced empty)
  #   mosip / sunbird     -> PKCS11 (SoftHSM deployed here, inline; extraEnvVarsCM set)
  # ---------------------------------------------------------------------
  if [[ "$plugin_name" == "mock" ]]; then
    echo "Plugin 'mock' selected - configuring PKCS12 keystore. No HSM required."

    # ---------------- PKCS12 flow ----------------
    default_volume_size=100M
    read -p "Provide the size for volume [ default : 100M ]: " volume_size
    volume_size=${volume_size:-$default_volume_size}

    default_volume_mount_path='/home/mosip/config/'
    read -p "Provide the mount path for volume [ default : '/home/mosip/config/' ] : " volume_mount_path
    volume_mount_path=${volume_mount_path:-$default_volume_mount_path}

    PVC_CLAIM_NAME='esignet-pkcs12'
    ESIGNET_HELM_ARGS=(
      --set persistence.enabled=true
      --set volumePermissions.enabled=true
      --set persistence.size=$volume_size
      --set persistence.mountDir=\"$volume_mount_path\"
      --set persistence.pvc_claim_name=\"$PVC_CLAIM_NAME\"
    )

    keystore_env_vars+="  KEYMANAGER_KEYSTORE_TYPE: \"PKCS12\""$'\n'

    default_pkcs12_file_path="/home/mosip/config/local.p12"
    read -p "Provide KEYMANAGER_PKCS12_FILE_PATH [default: $default_pkcs12_file_path]: " pkcs12_file_path
    pkcs12_file_path=${pkcs12_file_path:-$default_pkcs12_file_path}

    pkcs12_password=$(openssl rand -base64 16 | tr -dc 'A-Za-z0-9' | cut -c1-16)
    kubectl -n "$NS" create secret generic esignet-keymanager \
      --from-literal=pkcs12-password="$pkcs12_password" \
      --dry-run=client -o yaml | kubectl apply -f -
    echo "Generated KEYMANAGER_PKCS12_PASSWORD and stored it in secret 'esignet-keymanager' (key: pkcs12-password) in namespace '$NS'."

    keystore_env_vars+="  KEYMANAGER_PKCS12_FILE_PATH: \"$pkcs12_file_path\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS12_ALLOW_INSECURE_SOFTWARE_KEYSTORE: \"true\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS11_MODULE_PATH: \"\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS11_TOKEN_LABEL: \"\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS11_PIN: \"\""$'\n'
    keystore_env_vars+="  SOFTHSM_ESIGNET_SECURITY_PIN: \"\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS12_PASSWORD:"$'\n'
    keystore_env_vars+="    valueFrom:"$'\n'
    keystore_env_vars+="      secretKeyRef:"$'\n'
    keystore_env_vars+="        name: esignet-keymanager"$'\n'
    keystore_env_vars+="        key: pkcs12-password"$'\n'
    EXTRA_ENV_VARS_CM_YAML="extraEnvVarsCM: []"$'\n'

  else
    echo "Plugin '$plugin_name' selected - configuring PKCS11 keystore. SoftHSM required."

    prompt_hsm_choice
    EXTRA_ENV_VARS_CM_YAML="extraEnvVarsCM:"$'\n'"  - $SOFTHSM_CM_NAME"$'\n'
    default_pkcs11_module_path="/usr/local/lib/softhsm/libpkcs11-proxy.so"
    default_pkcs11_token_label="mosip-token"
    default_hsm_client_zip_url_env="https://raw.githubusercontent.com/mosip/artifactory-ref-impl/master/artifacts/src/hsm/client.zip"

    echo "The default esignet deployment already configures PKCS11 with:"
    echo "  KEYMANAGER_PKCS11_MODULE_PATH = $default_pkcs11_module_path"
    echo "  KEYMANAGER_PKCS11_TOKEN_LABEL = $default_pkcs11_token_label"
    echo "  hsm_client_zip_url_env        = $default_hsm_client_zip_url_env"

    while true; do
      read -p "Do you want to proceed with these default values? (y/n): " use_pkcs11_defaults
      if [[ "$use_pkcs11_defaults" == "y" || "$use_pkcs11_defaults" == "Y" ]]; then
        pkcs11_module_path="$default_pkcs11_module_path"
        pkcs11_token_label="$default_pkcs11_token_label"
        hsm_client_zip_url_env="$default_hsm_client_zip_url_env"
        break
      elif [[ "$use_pkcs11_defaults" == "n" || "$use_pkcs11_defaults" == "N" ]]; then
        read -p "Provide KEYMANAGER_PKCS11_MODULE_PATH [default: $default_pkcs11_module_path]: " pkcs11_module_path
        pkcs11_module_path=${pkcs11_module_path:-$default_pkcs11_module_path}

        read -p "Provide KEYMANAGER_PKCS11_TOKEN_LABEL [default: $default_pkcs11_token_label]: " pkcs11_token_label
        pkcs11_token_label=${pkcs11_token_label:-$default_pkcs11_token_label}

        read -p "Provide hsm_client_zip_url_env [default: $default_hsm_client_zip_url_env]: " hsm_client_zip_url_env
        hsm_client_zip_url_env=${hsm_client_zip_url_env:-$default_hsm_client_zip_url_env}
        break
      else
        echo "Please provide a correct option (y or n)."
      fi
    done

    keystore_env_vars+="  KEYMANAGER_KEYSTORE_TYPE: \"PKCS11\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS11_PIN:"$'\n'
    keystore_env_vars+="    valueFrom:"$'\n'
    keystore_env_vars+="      secretKeyRef:"$'\n'
    keystore_env_vars+="        name: \"$SOFTHSM_SERVICE_NAME\""$'\n'
    keystore_env_vars+="        key: security-pin"$'\n'
    keystore_env_vars+="  SOFTHSM_ESIGNET_SECURITY_PIN:"$'\n'
    keystore_env_vars+="    valueFrom:"$'\n'
    keystore_env_vars+="      secretKeyRef:"$'\n'
    keystore_env_vars+="        name: \"$SOFTHSM_SERVICE_NAME\""$'\n'
    keystore_env_vars+="        key: security-pin"$'\n'
    if [[ "$pkcs11_module_path" != "$default_pkcs11_module_path" ]]; then
      keystore_env_vars+="  KEYMANAGER_PKCS11_MODULE_PATH: \"$pkcs11_module_path\""$'\n'
    fi
    keystore_env_vars+="  KEYMANAGER_PKCS11_TOKEN_LABEL: \"$pkcs11_token_label\""$'\n'
    keystore_env_vars+="  hsm_client_zip_url_env: \"$hsm_client_zip_url_env\""$'\n'
    PKCS11_INSTALL_SCRIPT=${PKCS11_INSTALL_SCRIPT:-../pkcs11-install.sh}
    if [ -x "$PKCS11_INSTALL_SCRIPT" ]; then
      echo "Running PKCS11 installation script: $PKCS11_INSTALL_SCRIPT"
      hsm_client_zip_url_env="$hsm_client_zip_url_env" "$PKCS11_INSTALL_SCRIPT"
    else
      echo "WARNING: PKCS11 installation script not found or not executable at '$PKCS11_INSTALL_SCRIPT'."
      echo "Please ensure PKCS11 is installed/configured on the target nodes before proceeding, or set PKCS11_INSTALL_SCRIPT to the correct path."
    fi

    echo ""
    echo "SoftHSM generated a random security PIN and stored it in the '$SOFTHSM_SERVICE_NAME' secret."

    while true; do
      read -p "Do you want to keep the randomly generated PIN, or provide your own? (keep/provide): " pkcs11_pin_choice
      if [[ "$pkcs11_pin_choice" == "keep" || "$pkcs11_pin_choice" == "Keep" ]]; then
        echo "Keeping the randomly generated KEYMANAGER_PKCS11_PIN from the '$SOFTHSM_SERVICE_NAME' secret."
        break
      elif [[ "$pkcs11_pin_choice" == "provide" || "$pkcs11_pin_choice" == "Provide" ]]; then
        read -s -p "Enter your KEYMANAGER_PKCS11_PIN: " pkcs11_pin
        echo
        if [[ -z "$pkcs11_pin" ]]; then
          echo "No value entered; keeping the randomly generated PIN instead."
        else
          kubectl -n $NS patch secret "$SOFTHSM_SERVICE_NAME" --type='json' \
            -p="[{\"op\":\"replace\",\"path\":\"/data/security-pin\",\"value\":\"$(printf '%s' "$pkcs11_pin" | base64 | tr -d '\n')\"}]"
          echo "Updated the 'security-pin' key in the existing '$SOFTHSM_SERVICE_NAME' secret."
        fi
        break
      else
        echo "Please type 'keep' or 'provide'."
      fi
    done
  fi

  extra_env_vars_additional+="  \"MOSIP_ESIGNET_AUTHN_PROVIDER\": \"$plugin_name\""$'\n'
  extra_env_vars_additional+="  \"NAMESPACE\": \"$NS\""$'\n'

  if kubectl get secret esignet-captcha -n "$NS" &>/dev/null; then
    extra_env_vars_additional+="  MOSIP_ESIGNET_CAPTCHA_SITE_KEY:"$'\n'
    extra_env_vars_additional+="    valueFrom:"$'\n'
    extra_env_vars_additional+="      secretKeyRef:"$'\n'
    extra_env_vars_additional+="        name: esignet-captcha"$'\n'
    extra_env_vars_additional+="        key: esignet-captcha-site-key"$'\n'
  else
    extra_env_vars_additional+="  \"MOSIP_ESIGNET_CAPTCHA_SITE_KEY\": \"\""$'\n'
  fi

  plugin_env_file=$(mktemp)
  {
    if [[ -n "$keystore_env_vars" ]]; then
      echo "extraEnvVars:"
      printf '%s' "$keystore_env_vars"
    fi
    if [[ -n "$extra_env_vars_additional" ]]; then
      echo "extraEnvVarsAdditional:"
      printf '%s' "$extra_env_vars_additional"
    fi
    if [[ -n "$EXTRA_ENV_VARS_CM_YAML" ]]; then
      printf '%s' "$EXTRA_ENV_VARS_CM_YAML"
    fi
  } > "$plugin_env_file"

  plugin_option="--set pluginNameEnv=$plugin_name -f $plugin_env_file"

  echo Installing esignet
  helm -n $NS install $ESIGNET_SERVICE_NAME /home/techno-467/IdeaProjects/esignet/helm/esignet --version $CHART_VERSION  \
    -f values.yaml \
    $ENABLE_INSECURE \
    "${ESIGNET_HELM_ARGS[@]}" \
    --set metrics.serviceMonitor.enabled=$servicemonitorflag \
    --set-string extraEnvVars.MOSIP_ESIGNET_HOST="$MOSIP_ESIGNET_HOST" \
    --set-string extraEnvVars.MOSIP_ESIGNET_BASE_URL="$MOSIP_ESIGNET_BASE_URL" \
    $plugin_option --wait

  kubectl -n $NS get deploy $ESIGNET_SERVICE_NAME -o name | xargs -n1 -t kubectl -n $NS rollout status

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