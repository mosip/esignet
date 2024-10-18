#!/bin/bash
# Restart the captcha services
## Usage: ./restart.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

function Restarting_captcha() {
  NS=captcha
  kubectl -n $NS rollout restart deploy captcha

  kubectl -n $NS  get deploy captcha -o name |  xargs -n1 -t  kubectl -n $NS rollout status

  echo Restarted captcha services
  return 0
}

# set commands for error handling.
set -e
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errtrace  # trace ERR through 'time command' and other functions
set -o pipefail  # trace ERR through pipes
Restarting_captcha   # calling function
