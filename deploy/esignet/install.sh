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
# The mosip/softhsm chart names its ConfigMap after the release name with a
# "-share" suffix (observed: release "esignet-softhsm" -> configmap
# "esignet-softhsm-share"). Derived here so it always matches
# SOFTHSM_SERVICE_NAME above, however it's set. If the chart's actual naming
# differs, override SOFTHSM_CM_NAME directly as an env var before running.
SOFTHSM_CM_NAME=${SOFTHSM_CM_NAME:-${SOFTHSM_SERVICE_NAME}-share}
SOFTHSM_CHART_VERSION=12.0.1
echo Create $NS namespace
kubectl create ns $NS || true

# ---------------------------------------------------------------------
# SoftHSM / HSM deployment, inlined here (no longer a separate script).
# Only called from within the PKCS11 branch below, i.e. only for the
# mosip and sunbird plugins. The mock plugin (PKCS12) never reaches this.
# SOFTHSM_VALUES_PATH defaults to the sibling softhsm/ directory
# (softhsm/ and this esignet/ folder are siblings); override via env
# var if your layout differs.
# ---------------------------------------------------------------------
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
  # Always upgrade --install, whether or not the release already exists.
  # This is intentional: it means any change you make to softhsm-values.yaml
  # (token label, PIN, module path defaults, etc.) is picked up on every
  # run, rather than being silently ignored once SoftHSM is already
  # installed. upgrade --install is idempotent either way - fresh install
  # if the release doesn't exist, in-place upgrade if it does - so this
  # never fails with Helm's "cannot re-use a name that is still in use".
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
      # The chart/app requires a non-empty value here - creating this secret
      # with an empty string (the old behavior) leaves the mosip-esignet-misp-key
      # value blank, which prevents the esignet service from coming up. Default
      # to a non-empty placeholder; allow providing a real key if available.
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
    ESIGNET_HELM_ARGS="--set persistence.enabled=true  \
                       --set volumePermissions.enabled=true \
                       --set persistence.size=$volume_size \
                       --set persistence.mountDir=\"$volume_mount_path\" \
                       --set persistence.pvc_claim_name=\"$PVC_CLAIM_NAME\"  \
                      "

    keystore_env_vars+="  KEYMANAGER_KEYSTORE_TYPE: \"PKCS12\""$'\n'

    default_pkcs12_file_path="/home/mosip/config/local.p12"
    read -p "Provide KEYMANAGER_PKCS12_FILE_PATH [default: $default_pkcs12_file_path]: " pkcs12_file_path
    pkcs12_file_path=${pkcs12_file_path:-$default_pkcs12_file_path}

    # Generate a random KEYMANAGER_PKCS12_PASSWORD and store it as a Kubernetes
    # Secret in the same namespace, matching the chart's documented convention
    # (secretKeyRef: esignet-keymanager / pkcs12-password) instead of a plain value.
    pkcs12_password=$(openssl rand -base64 16 | tr -dc 'A-Za-z0-9' | cut -c1-16)
    kubectl -n "$NS" create secret generic esignet-keymanager \
      --from-literal=pkcs12-password="$pkcs12_password" \
      --dry-run=client -o yaml | kubectl apply -f -
    echo "Generated KEYMANAGER_PKCS12_PASSWORD and stored it in secret 'esignet-keymanager' (key: pkcs12-password) in namespace '$NS'."

    keystore_env_vars+="  KEYMANAGER_PKCS12_FILE_PATH: \"$pkcs12_file_path\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS12_ALLOW_INSECURE_SOFTWARE_KEYSTORE: \"true\""$'\n'

    # Mock uses PKCS12 exclusively - explicitly blank out the PKCS11-related
    # keys so no leftover/inherited PKCS11 config lingers in this deployment.
    keystore_env_vars+="  KEYMANAGER_PKCS11_MODULE_PATH: \"\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS11_TOKEN_LABEL: \"\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS11_SLOT_ID: \"\""$'\n'
    keystore_env_vars+="  KEYMANAGER_PKCS11_PIN: \"\""$'\n'

    # The chart's base extraEnvVars has a SECOND, separate key that also
    # points at the 'esignet-softhsm' secret (KEYMANAGER_PKCS11_PIN above
    # is the first) - without overriding this one too, the mock deployment
    # still fails with "secret esignet-softhsm not found" even after
    # KEYMANAGER_PKCS11_PIN is blanked.
    keystore_env_vars+="  SOFTHSM_ESIGNET_SECURITY_PIN: \"\""$'\n'

    keystore_env_vars+="  KEYMANAGER_PKCS12_PASSWORD:"$'\n'
    keystore_env_vars+="    valueFrom:"$'\n'
    keystore_env_vars+="      secretKeyRef:"$'\n'
    keystore_env_vars+="        name: esignet-keymanager"$'\n'
    keystore_env_vars+="        key: pkcs12-password"$'\n'

    # The esignet chart's own values.yaml defaults extraEnvVarsCM to
    # {esignet-softhsm-share} regardless of plugin. We must override it to
    # an empty list to cancel that default for mock. IMPORTANT: Helm's CLI
    # `--set list={}` does NOT create an empty list - it creates a list
    # containing one empty string, which breaks the deployment template
    # (envFrom[0].configMapRef.name: Required value). So instead we write
    # `extraEnvVarsCM: []` directly into the YAML overlay file below, where
    # an empty list is unambiguous.
    EXTRA_ENV_VARS_CM_YAML="extraEnvVarsCM: []"$'\n'

  else
    echo "Plugin '$plugin_name' selected - configuring PKCS11 keystore. SoftHSM required."

    # ---------------- SoftHSM / HSM deployment (inline) ----------------
    # SoftHSM is only required for PKCS11-backed plugins (mosip, sunbird),
    # which is exactly this branch. Runs before anything below references
    # the 'esignet-softhsm' secret (module path defaults, and the optional
    # PIN override/patch further down both assume that secret already exists).
    # Always prompts and always runs upgrade --install inside
    # installing_softhsm() (see that function for details) - this means
    # softhsm-values.yaml changes are picked up on every run, whether
    # SoftHSM already exists or not, rather than being skipped once
    # installed.
    prompt_hsm_choice

    # Only mosip/sunbird (this branch) reference the SoftHSM-provided
    # ConfigMap on the esignet helm install - mock never reaches here.
    # Written as YAML (not --set) for consistency with the mock branch.
    EXTRA_ENV_VARS_CM_YAML="extraEnvVarsCM:"$'\n'"  - $SOFTHSM_CM_NAME"$'\n'

    # ---------------- PKCS11 flow ----------------
    # NOTE: KEYMANAGER_PKCS11_PIN is intentionally NOT prompted for here.
    # values.yaml already defaults it to a secretKeyRef (esignet-softhsm/security-pin),
    # and that secret is created as part of the softhsm/PKCS11 install flow above.
    # Overriding it with a plain-text value here would replace a working secret
    # reference with a literal, so we leave the chart default untouched.
    # KEYMANAGER_PKCS11_TOKEN_LABEL and hsm_client_zip_url_env belong in the
    # esignet chart's deployment.yaml (extraEnvVarsAdditional), controlled
    # entirely from here: accept the default, or provide your own value.

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

    # The chart's own base extraEnvVars hardcodes secretKeyRef.name as the
    # literal string "esignet-softhsm" for both of these keys. That's only
    # correct when SOFTHSM_SERVICE_NAME is left at its default. Since the
    # name is overridable, we must always override these two here so they
    # point at whatever SoftHSM release name is actually in use - otherwise
    # a renamed release causes "secret <old-default-name> not found".
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

    # KEYMANAGER_PKCS11_MODULE_PATH: the esignet chart already has its own
    # default for this key, so only write it here if the user overrode it -
    # otherwise the chart's own default applies and we avoid duplicating it.
    if [[ "$pkcs11_module_path" != "$default_pkcs11_module_path" ]]; then
      keystore_env_vars+="  KEYMANAGER_PKCS11_MODULE_PATH: \"$pkcs11_module_path\""$'\n'
    fi

    # KEYMANAGER_PKCS11_TOKEN_LABEL and hsm_client_zip_url_env: unlike
    # MODULE_PATH, the esignet chart has NO built-in default for these two -
    # they only exist via SoftHSM's own values.yaml, which doesn't feed into
    # the esignet chart's env vars. So these must always be written here,
    # whether the user kept the default or provided their own value -
    # otherwise they end up completely absent from deployment.yaml.
    keystore_env_vars+="  KEYMANAGER_PKCS11_TOKEN_LABEL: \"$pkcs11_token_label\""$'\n'
    keystore_env_vars+="  hsm_client_zip_url_env: \"$hsm_client_zip_url_env\""$'\n'

    # Run the existing PKCS11 installation script/flow, if present.
    # Set PKCS11_INSTALL_SCRIPT env var before running this script to override the path.
    PKCS11_INSTALL_SCRIPT=${PKCS11_INSTALL_SCRIPT:-../pkcs11-install.sh}
    if [ -x "$PKCS11_INSTALL_SCRIPT" ]; then
      echo "Running PKCS11 installation script: $PKCS11_INSTALL_SCRIPT"
      hsm_client_zip_url_env="$hsm_client_zip_url_env" "$PKCS11_INSTALL_SCRIPT"
    else
      echo "WARNING: PKCS11 installation script not found or not executable at '$PKCS11_INSTALL_SCRIPT'."
      echo "Please ensure PKCS11 is installed/configured on the target nodes before proceeding, or set PKCS11_INSTALL_SCRIPT to the correct path."
    fi

    # ---------------- KEYMANAGER_PKCS11_PIN (optional override) ----------------
    # SoftHSM auto-generates a random security PIN during install (or during
    # a previous run, if the install was skipped above) and stores it in the
    # "$SOFTHSM_SERVICE_NAME" secret (key: security-pin). There is no fixed
    # default value to display here - it's random each time SoftHSM is
    # installed. KEYMANAGER_PKCS11_PIN above already references this secret
    # by name, so keeping the generated PIN requires no further action.
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

  # ---------------------------------------------------------------------
  # MOSIP_ESIGNET_AUTHN_PROVIDER is derived from the plugin selected above
  # (mock -> mock, mosip -> mosip, sunbird -> sunbird).
  # NAMESPACE holds the actual k8s namespace the chart is deployed into.
  # ---------------------------------------------------------------------
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

  # Combine env vars into a YAML overlay file with two distinct sections:
  # - extraEnvVars: keys the chart's OWN values.yaml already defines
  #   (KEYMANAGER_*). Helm deep-merges -f overlay maps against chart
  #   defaults, so setting a key here actually REPLACES the chart's
  #   default for that key (e.g. cancels its secretKeyRef pointing at the
  #   'esignet-softhsm' secret). This is required, not optional - writing
  #   these into extraEnvVarsAdditional only appends a second, separate env
  #   entry alongside the chart's original one; Kubernetes still tries to
  #   resolve the original secretKeyRef and fails with
  #   CreateContainerConfigError even though our override "wins" logically.
  # - extraEnvVarsAdditional: keys the chart does NOT predefine (custom
  #   MOSIP_ESIGNET_* vars, NAMESPACE, captcha) - these are genuinely
  #   additional, so appending them here is correct.
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
  helm -n $NS install $ESIGNET_SERVICE_NAME mosip/esignet --version $CHART_VERSION  \
    $ENABLE_INSECURE $plugin_option \
    $ESIGNET_HELM_ARGS \
    --set metrics.serviceMonitor.enabled=$servicemonitorflag -f values.yaml --wait

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