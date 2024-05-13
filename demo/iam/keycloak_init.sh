#!/bin/bash
# Initialize Keycloak with MOSIP base data
# Usage:
# ./keycloak_init.sh [kube_config_file]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

read_user_input(){
    if [ $# -lt 2 ]; then
        echo "$(tput setaf 1) Variable & Message arguments not passed to read_user_input function; EXITING $(tput sgr0)";
        exit 1;
    fi
    DEFAULT=''
    if [ $# -gt 2 ]; then
        DEFAULT=$3;                            ## default values for $VAR variable
    fi
    VAR=$1;                                    ## variable name
    MSG=$2;                                    ## message to be printed for the given variable
    read -p "Provide $MSG : " $VAR;
    TEMP=$( eval "echo \${$VAR}" );            ## save $VAR values to a temporary variable
    eval ${VAR}=${TEMP:-$DEFAULT};             ## set $VAR value to $DEFAULT if $TEMP is empty, else set $VAR value to $TEMP
    VAR_VALUE=$( eval "echo \${$VAR}" )
    if [ -z $VAR_VALUE ]; then
        echo "$(tput setaf 1) $MSG not provided; EXITING $(tput sgr0)";
        exit 1;
    fi

    if [[ $# -gt 3  ]]; then
      if echo "$VAR_VALUE" | grep -Ev "$4" > /dev/null; then
        echo "$(tput setaf 1) Variable $VAR is neither of $4 $(tput sgr0)";
        exit 1;
      fi
    fi
    DEFAULT='';                               ## reset `DEFAULT` variable to empty string
}

function initialize_keycloak() {
  NS=keycloak
  CHART_VERSION=12.0.2

  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update

  read_user_input SMTP_HOST "'SMTP host' for keycloak"
  read_user_input SMTP_PORT "'SMTP port' for keycloak"

  read_user_input SMTP_FROM_ADDR "'From email address' for keycloak SMTP"
  REGEX="^[a-z0-9!#\$%&'*+/=?^_\`{|}~-]+(\.[a-z0-9!#$%&'*+/=?^_\`{|}~-]+)*@([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z0-9]([a-z0-9-]*[a-z0-9])?\$"
  if [[ !  "$SMTP_FROM_ADDR" =~ $REGEX ]] ; then
      echo "$(tput setaf 1)  Variable SMTP_FROM_ADDR is not a valid email ID; EXITING;$(tput sgr0)"
      exit 1;
  fi
  read_user_input SMTP_STARTTLS "Would you like to enable 'starttls' configuration for SMTP ? (false/true) : [ Default: false ]" false '^(true|false)$'
  read_user_input SMTP_AUTH "Would you like to enable \"AUTHENTICATION\" configuration for SMTP ? (true/false) : [ Default: true ]" true '^(true|false)$'
  read_user_input SMTP_SSL "Would you like to enable \"SSL\" fro SMTP ? (true/false) : [ Default: true ]" true '^(true|false)$'
  SMTP_AUTH_SET="--set keycloak.realms.mosip.realm_config.smtpServer.auth=$SMTP_AUTH"
  if [[ $SMTP_AUTH == "true" ]]; then
    read_user_input SMTP_USERNAME "Provide SMTP login Username"
    read_user_input SMTP_PASSWORD "Provide SMTP login Password"

    SMTP_AUTH_SET="--set keycloak.realms.mosip.realm_config.smtpServer.auth=$SMTP_AUTH          \
          --set keycloak.realms.mosip.realm_config.smtpServer.user=$SMTP_USERNAME      \
          --set keycloak.realms.mosip.realm_config.smtpServer.password=$SMTP_PASSWORD"
  fi

  IAMHOST_URL=$(kubectl get cm global -o jsonpath={.data.mosip-iam-external-host})

  echo Initializing keycloak-init
  helm -n $NS install keycloak-init mosip/keycloak-init   \
  --set keycloak.realms.mosip.realm_config.smtpServer.host="$SMTP_HOST"                     \
  --set keycloak.realms.mosip.realm_config.smtpServer.port="$SMTP_PORT"                     \
  --set keycloak.realms.mosip.realm_config.smtpServer.from="$SMTP_FROM_ADDR"                \
  --set keycloak.realms.mosip.realm_config.smtpServer.starttls="$SMTP_STARTTLS"             \
  --set keycloak.realms.mosip.realm_config.smtpServer.ssl="$SMTP_SSL"                       \
  $SMTP_AUTH_SET \
  --set keycloak.realms.mosip.realm_config.attributes.frontendUrl="https://$IAMHOST_URL/auth" --version $CHART_VERSION
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
initialize_keycloak   # calling function
