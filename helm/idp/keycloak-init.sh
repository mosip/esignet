#!/bin/sh
# Installs all idp keycloak-init
## Usage: ./keycloak-init.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=idp
CHART_VERSION=12.0.2
COPY_UTIL=../copy_cm_func.sh

IAMHOST_URL=$(kubectl get cm global -o jsonpath={.data.mosip-iam-external-host})
PMS_CLIENT_PASSWORD=$( kubectl -n keycloak get secrets keycloak-client-secrets -o jsonpath={.data.mosip_pms_client_secret} | base64 -d )
MPARTNER_DEFAULT_AUTH_CLIENT_PASSWORD=$( kubectl -n keycloak get secrets keycloak-client-secrets -o jsonpath={.data.mpartner_default_auth_secret} | base64 -d )

echo "Copying keycloak configmaps and secret"
$COPY_UTIL configmap keycloak-host keycloak $NS
$COPY_UTIL configmap keycloak-env-vars keycloak $NS
$COPY_UTIL secret keycloak keycloak $NS

echo "creating and adding roles to keycloak pms client for IDP"

helm -n $NS delete idp-keycloak-init
helm -n $NS install idp-keycloak-init mosip/keycloak-init \
-f keycloak-init-values.yaml \
--set frontend=https://$IAMHOST_URL/auth \
--set clientSecrets[0].name="mosip_pms_client_secret" \
--set clientSecrets[0].secret="$PMS_CLIENT_PASSWORD" \
--set clientSecrets[1].name="mpartner_default_auth_secret" \
--set clientSecrets[1].secret="$MPARTNER_DEFAULT_AUTH_CLIENT_PASSWORD" \
--version $CHART_VERSION
