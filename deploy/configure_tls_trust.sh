#!/bin/bash
# Configures Helm arguments for eSignet TLS trust settings.
# Usage: configure_tls_trust <namespace>
# Exports TRUST_HELM_ARGS for the caller to pass to Helm.

configure_tls_trust() {
  local ns="${1:?namespace is required}"
  local ca_secret ca_file ca_secret_key available_keys
  export TRUST_HELM_ARGS=''

  secret_has_key() {
    kubectl -n "$1" get secret "$2" -o "go-template={{if index .data \"${3}\"}}present{{end}}" | grep -q present
  }

  echo "TLS trust configuration:"
  echo "1) Public domain with valid SSL certificate (default)"
  echo "2) Development/self-signed workaround (enable_insecure)"
  echo "3) Company internal CA"
  read -r -p "Choose option [1/2/3] (default: 1): " ssl_choice
  ssl_choice=${ssl_choice:-1}

  case "$ssl_choice" in
    2)
      echo "Using enable_insecure for development/self-signed environments."
      export TRUST_HELM_ARGS='--set enable_insecure=true'
      ;;
    3)
      read -r -p "Name of the Secret containing PEM-encoded CA cert(s) [company-internal-ca]: " ca_secret
      ca_secret=${ca_secret:-company-internal-ca}
      ca_secret_key="ca.crt"

      if kubectl -n "$ns" get secret "$ca_secret" >/dev/null 2>&1; then
        if ! secret_has_key "$ns" "$ca_secret" "$ca_secret_key"; then
          available_keys=$(kubectl -n "$ns" get secret "$ca_secret" -o go-template='{{range $k,$v := .data}}{{printf "%s " $k}}{{end}}')
          echo "Key '$ca_secret_key' not found in Secret '$ca_secret'. Available keys: ${available_keys}"
          read -r -p "Enter the Secret data key containing the PEM bundle: " ca_secret_key_input
          ca_secret_key=${ca_secret_key_input:-ca.crt}
          if ! secret_has_key "$ns" "$ca_secret" "$ca_secret_key"; then
            echo "Key '$ca_secret_key' not found in Secret '$ca_secret'; EXITING"
            exit 1
          fi
        fi
      else
        read -r -p "Secret '$ca_secret' not found. Provide path to PEM CA bundle file: " ca_file
        if [[ -z "${ca_file}" || ! -f "${ca_file}" ]]; then
          echo "CA bundle file not found; EXITING"
          exit 1
        fi
        kubectl -n "$ns" create secret generic "$ca_secret" \
          --from-file="${ca_secret_key}=${ca_file}" \
          --dry-run=client -o yaml | kubectl apply -f -
        echo "Created Secret '$ca_secret' with company CA bundle."
      fi

      export TRUST_HELM_ARGS="--set customCA.enabled=true --set customCA.secretName=${ca_secret} --set customCA.secretKey=${ca_secret_key}"
      ;;
    1|*)
      echo "Using default Java truststore (public CAs)."
      ;;
  esac
}
