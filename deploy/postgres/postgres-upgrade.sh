#!/bin/bash
# Script to upgrade esignet DB.
## Usage: ./postgres-upgrade.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi
read_user_input(){
    if [ $# -lt 2 ]; then
        echo "$(tput setaf 1) Variable & Message arguments not passed to read_user_input function; EXITING $(tput sgr0)";
        exit 1;
    fi
    DEFAULT='';
    if [ $# -gt 2 ]; then
        DEFAULT=$3;                            ## default values for $VAR variable
    fi
    VAR=$1;                                    ## variable name
    MSG=$2;                                    ## message to be printed for the given variable
    read -p " Please Provide $MSG : " $VAR;
    TEMP=$( eval "echo \${$VAR}" );            ## save $VAR values to a temporary variable
    eval ${VAR}=${TEMP:-$DEFAULT};             ## set $VAR value to $DEFAULT if $TEMP is empty, else set $VAR value to $TEMP
    if [ -z $( eval "echo \${$VAR}" ) ]; then
        echo "$(tput setaf 1) $MSG not provided; EXITING $(tput sgr0)";
        exit 1;
    fi
    DEFAULT='';                               ## reset `DEFAULT` variable to empty string
}
function initialize_db() {
  NS=esignet
  CHART_VERSION=12.0.1
  helm repo add mosip https://mosip.github.io/mosip-helm
  helm repo update
  while true; do
      read -p "Is 'upgrade.csv' for DB Upgrade set correctly as part of Pre-requisites?(Y/n) " yn
      if [ $yn = "Y" ]
        then
          echo "Creating upgrade-csv configmap"
          if ! [ -f 'upgrade.csv' ]; then
            echo "'upgrade.csv' not found; EXITING;"
            exit 1;
          fi
          kubectl -n $NS delete configmap --ignore-not-found=true  postgres-upgrade
          kubectl -n $NS create configmap postgres-upgrade --from-file=./upgrade.csv

          read_user_input PRIMARY_LANGUAGE_CODE "Enter the primary lang. code";
          read_user_input DB_SERVERIP "Database server host";
          read_user_input DB_PORT "Database server port";
          read_user_input SU_USER "Database super username";

          read -s -p "Enter Database super user password: " SU_USER_PWD
          read -s -p "Enter Database user password: " DB_USER_PWD

          helm -n $NS install postgres-upgrade mosip/postgres-upgrade \
          --set database.host="$DB_SERVERIP" \
          --set database.port="$DB_PORT" \
          --set database.su.user="$SU_USER" \
          --set database.plangcode="$PRIMARY_LANGUAGE_CODE" \
          --set database.su.su_password="$SU_USER_PWD" \
          --set database.dbu.dbu_password="$DB_USER_PWD" \
          --version $CHART_VERSION \
          --wait --wait-for-jobs
          break
        else
          break
      fi
  done
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
initialize_db   # calling function
