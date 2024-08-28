#!/bin/bash
# Creates configmap and secrets for S3/Minio
# Specific "" for region for minio local installation
## Usage: ./install.sh [kubeconfig]

if [ $# -ge 3 ] ; then
  export KUBECONFIG=$4
fi

NS=s3

echo Create $NS namespace
kubectl create ns $NS 

function installing_Cred() {
  echo Istio label
  kubectl label ns $NS istio-injection=enabled --overwrite

  echo Select the type of object-store to be used:
  echo 1: For minio native using helm charts
  echo 2: For any other s3 object store like AWS
  while read -p "Please choose the correct option as mentioned above(1/2)" choice
  do
    if [ $choice = "1" ]
    then
    read -p "Please provide pretext value : " PRETEXT_VALUE
    echo Creating secrets as per Minio native installation
    USER=$(kubectl -n minio get secret minio -o jsonpath='{.data.root-user}' | base64 --decode)
    PASS=$(kubectl -n minio get secret minio -o jsonpath='{.data.root-password}' | base64 --decode)
    kubectl -n s3 create configmap s3 --from-literal=s3-user-key=$USER --from-literal=s3-region="" --dry-run=client  -o yaml | kubectl apply -f -
    kubectl -n s3 create secret generic s3 --from-literal=s3-user-secret=$PASS --from-literal=s3-pretext-value=$PRETEXT_VALUE --dry-run=client  -o yaml | kubectl apply -f -
    echo object-store secret and config map is set now.
    break
    elif [ $choice = "2" ]
    then
    read -p "Enter the S3 user key " USER
    read -p "Enter the S3 secret key" PASS
    read -p "Enter the S3 region" REGION
    read -p "Please provide pretext value : " PRETEXT_VALUE
    kubectl -n s3 create configmap s3 --from-literal=s3-user-key=$USER --from-literal=s3-region=$REGION --dry-run=client  -o yaml | kubectl apply -f -
    kubectl -n s3 create secret generic s3 --from-literal=s3-user-secret=$PASS --dry-run=client  -o yaml | kubectl apply -f -
    kubectl -n s3 create secret generic s3 --from-literal=s3-user-secret=$PASS --from-literal=s3-pretext-value=$PRETEXT_VALUE --dry-run=client  -o yaml | kubectl apply -f -
    echo object-store secret and config map is set now.
    break
    else
    echo You entered wrong choice
    kill -9 `ps --pid $$ -oppid=`; exit
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
installing_Cred   # calling function
