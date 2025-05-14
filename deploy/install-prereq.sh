#!/bin/bash

# Installs prerequisite services for Esignet
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ]; then
  export KUBECONFIG=$1
fi

ROOT_DIR=$(pwd)
NS=esignet
kubectl create ns $NS || true

function prompt_for_deployment() {
  local module_name=$1
  local prompt_message=$2
  local response

  if [[ "$module_name" == "hsm" ]]; then
    while true; do
      read -p "$prompt_message" response
      response=${response,,}  # convert to lowercase
      if [[ "$response" =~ ^[sepn]$ ]]; then
        break
      else
        echo "Incorrect input. Please enter one of the following: 's' for SoftHSM, 'e' for External HSM, 'p' for PKCS12, or 'n' to skip." >&2
      fi
    done
    echo "$response"
    return 0
  fi

  while true; do
    read -p "$prompt_message" response
    response=${response:-y}
    if [[ "$response" =~ ^[yYnN]$ ]]; then
      break
    else
      echo "Incorrect input. Please enter 'y' or 'n'." >&2
    fi
  done

  if [[ "$response" == "y" || "$response" == "Y" ]]; then
    if [[ "$module_name" != "apiaccesscontrol" ]]; then
      cd "$ROOT_DIR/$module_name"
      ./install.sh
    fi
  fi

  echo "$response"

}

function prompt_for_input() {
  local var_name=$1
  local prompt_message=$2
  read -p "$prompt_message" input
  eval "$var_name='$input'"
}

function installing_prerequisites() {

  echo "Creating esignet-global configmap in esignet namespace"
  kubectl -n $NS apply -f esignet-global-cm.yaml

  declare -a modules=("istio-gateway" "postgres" "kafka" "redis" "hsm" "captcha" "apiaccesscontrol")
  declare -A prompts=(
    ["hsm"]="Do you want to deploy hsm for esignet service? Please opt for 'n' if you already have hsm installed :(s - for softhsm, e - external, p - for pkcs12 based key management from mounted file): "
    ["kafka"]="Do you want to deploy Kafka in the kafka namespace? Please opt for 'n' if you already have a kafka deployed: Press enter for default y: "
    ["redis"]="Do you want to deploy redis in the redis namespace? Please opt for 'n' if you already have a redis deployed  : Press enter for default y: "
    ["apiaccesscontrol"]="Do you want to access control the esignet client management APIs: Please opt for 'n' if not required. Press enter for default y: "
  )

  echo "Installing prerequisite services"

  for module in "${modules[@]}"; do
    if [[ "$module" == "istio-gateway" || "$module" == "captcha" || "$module" == "postgres" ]]; then
      cd "$ROOT_DIR/$module"
      ./install.sh
    elif [[ -n "${prompts[$module]}"  ]]; then
      response=$(prompt_for_deployment "$module" "${prompts[$module]}")

      if [[ "$module" == "hsm" ]]; then
        if [[ "$response" == "e" ]]; then
          prompt_for_input externalhsmclient "Please provide the URL where externalhsm client zip is located: "
          prompt_for_input externalhsmhosturl "Please provide the host URL for externalhsm: "
          prompt_for_input externalhsmpassword "Please provide the password for the externalhsm: "

          echo "Creating ConfigMap for external HSM client and host URL"
          kubectl create configmap esignet-softhsm-share --from-literal=hsm_client_zip_url_env="$externalhsmclient" --from-literal=PKCS11_PROXY_SOCKET="$externalhsmhosturl" -n softhsm --dry-run=client -o yaml | kubectl apply -f -

          echo "Creating Secret for external HSM password"
          kubectl create secret generic esignet-softhsm --from-literal=security-pin="$externalhsmpassword" -n softhsm --dry-run=client -o yaml | kubectl apply -f -

        elif [[ "$response" == "p" ]]; then
          echo "To proceed with PKCS12, you will be asked to enable volume mounting during eSignet installation. Please make sure to enable it when prompted."
        elif [[ "$response" == "s" ]]; then
          cd "$ROOT_DIR/softhsm"
          ./install.sh
        fi
      fi

      if [[ "$module" == "kafka" && "$response" == "n" ]]; then
        prompt_for_input kafkaurl "Please provide the kafka URL: "
        echo "Creating ConfigMap for Kafka URL"
        #kubectl patch configmap esignet-global -n $NS --type merge -p "{\"data\": {\"mosip-kafka-host\": \"$kafkaurl\"}}"
        kubectl create configmap kafka-config --from-literal=SPRING_KAFKA_BOOTSTRAP-SERVERS="$kafkaurl" -n $NS --dry-run=client -o yaml | kubectl apply -f -
      fi

      if [[ "$module" == "redis" && "$response" == "n" ]]; then
        prompt_for_input redishostname "Please provide the hostname for the redis server: "
        prompt_for_input redisport "Please provide the port number for the redis server: "
        prompt_for_input redispassword "Please provide the password for the redis server: "
        echo "Creating ConfigMap for Redis"
        kubectl create configmap redis-config --from-literal=redis-host="$redishostname" --from-literal=redis-port="$redisport" -n redis --dry-run=client -o yaml | kubectl apply -f -
        echo "Creating Secret for Redis password"
        kubectl create secret generic redis --from-literal=redis-password="$redispassword" -n redis --dry-run=client -o yaml | kubectl apply -f -
      fi

      if [[ "$module" == "apiaccesscontrol"  && "$response" == "n"  ]]; then
          echo "Warning! You have chosen to skip the IAM initialization. The internal API’s of eSignet will run without access control."
        elif [[ "$response" == "y" ]]; then
          prompt_for_input iamserverurl "Please provide the IAM server URL: Press enter to install default IAM for access control: "
          if [[ -n "$iamserverurl" ]]; then
            echo "Creating ConfigMap for IAM server URL"
            kubectl create configmap keycloak-host --from-literal=keycloak-external-url="$iamserverurl" -n $NS --dry-run=client -o yaml | kubectl apply -f -
          else
            echo "No IAM server URL provided. Default IAM will be installed."
            cd "$ROOT_DIR/keycloak"
            ./install.sh
          fi
        fi


    fi
  done

  echo "All prerequisite services deployed successfully."
  return 0
}

# Set commands for error handling.
set -e
set -o errexit   ## Exit the script if any statement returns a non-true return value
set -o nounset   ## Exit the script if you try to use an uninitialized variable
set -o errtrace  ## Trace ERR through 'time command' and other functions
set -o pipefail  ## Trace ERR through pipes

# Calling the function to start installing prerequisites
installing_prerequisites
