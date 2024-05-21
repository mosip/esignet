#!/bin/bash
# Export Keycloak
## Usage: ./export.sh [kubeconfig]

## script starts from here
function export_keycloak() {
  read -p "Provide kubernetes cluster config file path : " K8S_CONFIG
  if  [ -z "$K8S_CONFIG" ]; then
    echo "Cluster config file path not provided; EXITING;";
    exit 1;
  fi
  if [ ! -f "$K8S_CONFIG" ]; then
    echo "Cluster config file $K8S_CONFIG not found; EXITING;";
    exit 1;
  fi

  read -p "Provide keycloak namespace ( Default namespace: default ) : " NAMESPACE
  if [ -z "$NAMESPACE" ]; then
    NAMESPACE=default
  fi

  read -p "Provide directory location for export files ( Default Location: current directory ) : " EXPORT_DIR
  if  [ -z "$EXPORT_DIR" ]; then
    EXPORT_DIR="keycloak-export"
  fi
  mkdir -p $EXPORT_DIR && echo "Created Export Directory : $EXPORT_DIR"
  if [ ! -d "$EXPORT_DIR" ]; then
    echo "Directory Location $EXPORT_DIR not found; EXITING;";
    exit 1;
  fi

  read -p "Provide \"No of users per file\" ( Default: 1000, Recommended value: total number of users ) : "  USERS_PER_FILE
  if  [ -z "$USERS_PER_FILE" ]; then
    USERS_PER_FILE=1000
  fi

  export KUBECONFIG=$K8S_CONFIG

  echo "  CLUSTER CONFIG FILE : $KUBECONFIG"
  echo "  NAMESPACE : $NAMESPACE"
  echo "  EXPORT_DIR : $EXPORT_DIR"
  echo "  NUMBER OF USERS PER FILE : $USERS_PER_FILE"

  KEYCLOAK_POD_ID=$( kubectl -n $NAMESPACE get pods |awk '( !/init/ && !/postgresql/ ) && /keycloak/{print $1}'  | head -1  2>&1);

  echo "  KEYCLOAK POD ID : $KEYCLOAK_POD_ID"

  kubectl -n $NAMESPACE exec -it $KEYCLOAK_POD_ID -- mkdir -p /tmp/keycloak-export/;

  echo "$(tput setaf 3)Press \"CTRL+C\" once after \"Export finished successfully\" is displayed !!! $(tput sgr0)"

  kubectl -n $NAMESPACE exec -it $KEYCLOAK_POD_ID -- /opt/jboss/tools/docker-entrypoint.sh \
  -Djboss.socket.binding.port-offset=100 -Dkeycloak.migration.action=export \
  -Dkeycloak.migration.provider=dir \
  -Dkeycloak.migration.realmName=mosip \
  -Dkeycloak.migration.usersExportStrategy=DIFFERENT_FILES \
  -Dkeycloak.migration.usersPerFile=$USERS_PER_FILE \
  -Dkeycloak.migration.file=/tmp/keycloak-export/ | grep 'Export finished successfully'

  kubectl -n $NAMESPACE exec -it $KEYCLOAK_POD_ID -- bash -c "cd tmp/keycloak-export/ && tar -czvf /tmp/keycloak-export.zip ." \
  && echo "Zipped keycloak-export files as keycloak-export.zip inside the keycloak pod !!!"

  kubectl cp $NAMESPACE/$KEYCLOAK_POD_ID:tmp/keycloak-export.zip   $EXPORT_DIR.zip \
  && echo "Copied keycloal-export zip file from keycloak pod "

  tar -xvzf $EXPORT_DIR.zip -C $EXPORT_DIR \
  && echo "Unzipped keycloak-export file $EXPORT_DIR"

  echo "Successfully exported keycloak realm data to location : $EXPORT_DIR/mosip-realm.json "
  echo "Successfully exported keycloak users data to location : $EXPORT_DIR/mosip-users-*.json"
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
export_keycloak   # calling function