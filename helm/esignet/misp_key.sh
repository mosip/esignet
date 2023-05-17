#!/usr/bin/env bash

#get date
date=$(date --utc +%FT%T.%3NZ)
AUTHMANAGER_URL="https://$(kubectl get configmap global -o json | jq -r '.data."mosip-api-internal-host"')"
KEYCLOAK_CLIENT_ID=mosip-pms-client
KEYCLOAK_CLIENT_SECRET="$(kubectl get secret keycloak-client-secrets -n keycloak -o json | jq -r '.data."mosip_pms_client_secret"' | base64 --decode)"
AUTH_APP_ID=partner
MISP_PARTNER_ID=mpartner-default-esignet


#echo -e "\n Generating misp license Keys \n";
#echo "AUTHMANAGER URL : $AUTHMANAGER_URL"
#echo "$MISP_PARTNER_ID"
#echo "* Request for authorization"
curl -s -D - -o /dev/null -X "POST" \
  "$AUTHMANAGER_URL/v1/authmanager/authenticate/clientidsecretkey" \
  -H "accept: */*" \
  -H "Content-Type: application/json" \
  -d '{
  "id": "string",
  "version": "string",
  "requesttime": "'$date'",
  "metadata": {},
  "request": {
    "clientId": "'$KEYCLOAK_CLIENT_ID'",
    "secretKey": "'$KEYCLOAK_CLIENT_SECRET'",
    "appId": "'$AUTH_APP_ID'"
  }
}' > temp.txt 2>&1 &

sleep 10
TOKEN=$( cat temp.txt | awk '/[aA]uthorization:/{print $2}' | sed -z 's/\n//g' | sed -z 's/\r//g')

if [[ -z $TOKEN ]]; then
  echo "Unable to Authenticate with authmanager. \"TOKEN\" is empty; EXITING";
  exit 1;
fi

#echo -e "\nGot Authorization token from authmanager"

curl -X "GET" \
  -H "Accept: application/json" \
  --cookie "Authorization=$TOKEN" \
  "$AUTHMANAGER_URL/v1/partnermanager/misps/$MISP_PARTNER_ID/licenseKey" > result.txt

cat result.txt | jq -r '.response.licenseKey'