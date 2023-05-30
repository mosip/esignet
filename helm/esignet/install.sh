#!/bin/sh
# Installs all esignet helm charts
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=esignet
CHART_VERSION=1.0.1

kubectl create ns $NS

helm repo update

./keycloak-init.sh

echo Copy configmaps
./copy_cm.sh

echo "Do you have public domain & valid SSL? (Y/n) "
echo "Y: if you have public domain & valid ssl certificate"
echo "n: If you don't have a public domain and a valid SSL certificate. Note: It is recommended to use this option only in development environments."
read -p "" flag

if [ -z "$flag" ]; then
  echo "'flag' was provided; EXITING;"
  exit 1;
fi
ENABLE_INSECURE=''
if [ "$flag" = "n" ]; then
  ENABLE_INSECURE='--set enable_insecure=true';
fi

MISPKEY=$(bash misp_key.sh)
echo "MISP License key is: $MISPKEY"

echo Setting up onboarder-keys secrets
kubectl -n $NS create secret generic onboarder-keys --from-literal=mosip-esignet-misp-key=$MISPKEY --dry-run=client -o yaml | kubectl apply -f -

./copy_cm_func.sh secret onboarder-keys esignet config-server

kubectl -n config-server set env --keys=mosip-esignet-misp-key --from secret/onboarder-keys deployment/config-server --prefix=SPRING_CLOUD_CONFIG_SERVER_OVERRIDES_

kubectl -n config-server get deploy -o name |  xargs -n1 -t  kubectl -n config-server rollout status

echo Installing esignet
helm -n $NS install esignet mosip/esignet --version $CHART_VERSION $ENABLE_INSECURE

kubectl -n $NS  get deploy -o name |  xargs -n1 -t  kubectl -n $NS rollout status

echo Installed esignet service
