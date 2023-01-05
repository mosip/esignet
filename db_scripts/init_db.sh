#!/bin/sh
# Script to initialize IDP DB.
## Usage: ./init_db.sh [kubeconfig]

if [ $# -ge 1 ] ; then
  export KUBECONFIG=$1
fi

NS=idp
CHART_VERSION=12.0.1-B2

helm repo add mosip https://mosip.github.io/mosip-helm
helm repo update

while true; do
    read -p "CAUTION: Do we already have Postgres installed? Also make sure the IDP DB is backed up as the same will be overriden. Do you still want to continue?" yn
    if [ $yn = "Y" ]
      then
        DB_USER_PASSWORD=$( kubectl -n postgres get secrets db-common-secrets -o jsonpath={.data.db-dbuser-password} | base64 -d )

        echo Removing existing mosip_idp DB installation
        helm -n $NS delete postgres-init-idp

        echo Copy Postgres secrets
        ./copy_cm_func.sh secret postgres-postgresql postgres $NS

        echo Initializing DB
        helm -n $NS install postgres-init-idp mosip/postgres-init -f init_values.yaml \
        --version $CHART_VERSION \
        --set dbUserPasswords.dbuserPassword="$DB_USER_PASSWORD" \
        --wait --wait-for-jobs
        break
      else
        break
    fi
done