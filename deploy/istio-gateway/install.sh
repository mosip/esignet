#!/bin/bash
# Install ingress gateways

#NOTE: istioctl is specific to kubeconfig file. If you've more than one config files, please specify them like mentioned below:
#istioctl --kubeconfig <path-to-config-file> or use -c as shorthand for --kubeconfig.

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function gateway() {

NS=istio-system

echo Installing gateways, proxy protocol, authpolicies
PUBLIC=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-api-host})
INTERNAL=$(kubectl -n esignet get cm esignet-global -o jsonpath={.data.mosip-api-internal-host})
echo Public domain: $PUBLIC
echo Internal dome: $INTERNAL


##helm -n istio-system install istio-addons chart/istio-addons --set gateway.public.host=$PUBLIC --set gateway.internal.host=$INTERNAL --set proxyProtocol.enabled=false

# Check if the internal gateway exists
internal_exists=$(kubectl get gateway internal -n istio-system --ignore-not-found=true)

# Check if the public gateway exists
public_exists=$(kubectl get gateway public -n istio-system --ignore-not-found=true)

if [[ -n "$public_exists" && -z "$internal_exists" ]]; then
  echo "Public gateway is present, but internal is not."
  gateway_option="--set gateway.public.enabled=false --set gateway.internal.host=$INTERNAL"
elif [[ -n "$internal_exists" && -z "$public_exists" ]]; then
  echo "Internal gateway is present, but public is not."
  gateway_option="--set gateway.public.host=$PUBLIC --set gateway.internal.enabled=false"
elif [[ -z "$public_exists" && -z "$internal_exists" ]]; then
  echo "Neither public nor internal gateway is present."
  gateway_option="--set gateway.public.host=$PUBLIC --set gateway.internal.host=$INTERNAL"
fi

if [[ -n "$public_exists" && -n "$internal_exists" ]]; then
  echo "Both public and internal gateways exist. Skipping installation."
else
  helm -n $NS install istio-addons . \
    $gateway_option \
    --set proxyProtocol.enabled=false
    --wait
fi

}

# set commands for error handling.
set -e
set -o errexit   # exit the script if any statement returns a non-true return value
set -o nounset   # exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
gateway
