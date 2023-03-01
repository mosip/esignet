#!/bin/sh
# Installs all esignet keycloak-init
## Usage: ./keycloak-init.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=12.0.1-B2
COPY_UTIL=../copy_cm_func.sh

helm repo add mosip https://mosip.github.io/mosip-helm
helm repo update

echo "checking if PMS & mpartner_default_auth client is created already"
IAMHOST_URL=$(kubectl get cm global -o jsonpath={.data.mosip-iam-external-host})
PMS_CLIENT_SECRET_KEY='mosip_pms_client_secret'
PMS_CLIENT_SECRET_VALUE=$( kubectl -n keycloak get secrets keycloak-client-secrets -o jsonpath={.data.$PMS_CLIENT_SECRET_KEY} | base64 -d )
MPARTNER_DEFAULT_AUTH_SECRET_KEY='mpartner_default_auth_secret'
MPARTNER_DEFAULT_AUTH_SECRET_VALUE=$( kubectl -n keycloak get secrets keycloak-client-secrets -o jsonpath={.data.$MPARTNER_DEFAULT_AUTH_SECRET_KEY} | base64 -d )

echo "Copying keycloak configmaps and secret"
$COPY_UTIL configmap keycloak-host keycloak $NS
$COPY_UTIL configmap keycloak-env-vars keycloak $NS
$COPY_UTIL secret keycloak keycloak $NS

echo "creating and adding roles to keycloak pms & mpartner_default_auth clients for ESIGNET"
kubectl -n $NS delete secret  --ignore-not-found=true keycloak-client-secrets
helm -n $NS delete esignet-keycloak-init
helm -n $NS install esignet-keycloak-init mosip/keycloak-init \
-f keycloak-init-values.yaml \
--set frontend=https://$IAMHOST_URL/auth \
--set clientSecrets[0].name="$PMS_CLIENT_SECRET_KEY" \
--set clientSecrets[0].secret="$PMS_CLIENT_SECRET_VALUE" \
--set clientSecrets[1].name="$MPARTNER_DEFAULT_AUTH_SECRET_KEY" \
--set clientSecrets[1].secret="$MPARTNER_DEFAULT_AUTH_SECRET_VALUE" \
--version $CHART_VERSION

MPARTNER_DEFAULT_AUTH_SECRET_VALUE=$( kubectl -n keycloak get secrets keycloak-client-secrets -o jsonpath={.data.$MPARTNER_DEFAULT_AUTH_SECRET_KEY} )
PMS_CLIENT_SECRET_VALUE=$( kubectl -n keycloak get secrets keycloak-client-secrets -o jsonpath={.data.$PMS_CLIENT_SECRET_KEY} )

kubectl -n keycloak get secret keycloak-client-secrets -o json | jq ".data[\"$PMS_CLIENT_SECRET_KEY\"]=\"$PMS_CLIENT_SECRET_VALUE\"" | jq ".data[\"$MPARTNER_DEFAULT_AUTH_SECRET_KEY\"]=\"$MPARTNER_DEFAULT_AUTH_SECRET_VALUE\"" | kubectl apply -f -
kubectl -n config-server get secret keycloak-client-secrets -o json | jq ".data[\"$PMS_CLIENT_SECRET_KEY\"]=\"$PMS_CLIENT_SECRET_VALUE\"" | jq ".data[\"$MPARTNER_DEFAULT_AUTH_SECRET_KEY\"]=\"$MPARTNER_DEFAULT_AUTH_SECRET_VALUE\"" | kubectl apply -f -

echo "Check the existence of the secret & host placeholder & pass the secret & ESIGNET host to config-server deployment if the placeholder does not exist."
ESIGNET_HOST_PLACEHOLDER=$( kubectl -n config-server get deployment -o json | jq -c '.items[].spec.template.spec.containers[].env[]| select(.name == "SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_MOSIP_ESIGNET_HOST")|.name' )
if [ -z $ESIGNET_HOST_PLACEHOLDER ]; then
  kubectl -n config-server set env --keys=mosip-esignet-host --from configmap/global deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_
  echo "Waiting for config-server to be Up and running"
  kubectl -n config-server rollout status deploy/config-server
fi
PMS_CLIENT_SECRET_PLACEHOLDER=$( kubectl -n config-server get deployment -o json | jq -c '.items[].spec.template.spec.containers[].env[]| select(.name == "SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_MOSIP_PMS_CLIENT_SECRET")|.name' )
if [ -z $PMS_CLIENT_SECRET_PLACEHOLDER ]; then
  kubectl -n config-server set env --keys=$PMS_CLIENT_SECRET_KEY --from secret/keycloak-client-secrets deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_
  echo "Waiting for config-server to be Up and running"
  kubectl -n config-server rollout status deploy/config-server
fi
MPARTNER_DEFAULT_AUTH_SECRET_PLACEHOLDER=$( kubectl -n config-server get deployment -o json | jq -c '.items[].spec.template.spec.containers[].env[]| select(.name == "SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_MPARTNER_DEFAULT_AUTH_SECRET")|.name' )
if [ -z $MPARTNER_DEFAULT_AUTH_SECRET_PLACEHOLDER ]; then
  kubectl -n config-server set env --keys=$MPARTNER_DEFAULT_AUTH_SECRET_KEY --from secret/keycloak-client-secrets deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_
  echo "Waiting for config-server to be Up and running"
  kubectl -n config-server rollout status deploy/config-server
fi
