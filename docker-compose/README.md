## Overview

This is the docker-compose setup to run esignet UI and esignet-service with mock identity system. This is not for production use.

## What is in the docker-compose setup folder?

1. "app" folder holds the Dockerfile required to build custom artifactory-server. This artifactory server will host all the files under app/static folder.
All the i18n bundles, dummy softhsm conf, signin-with-esignet button plugin files are served from this server. 
2. "config" folder holds the esignet and mock-identity system properties file.
3. "docker-compose.yml" file with esignet and mock-identity-system setup with other required services
4. "init.sql" comprises DDL and DMLs required by esignet and mock-identity-system.
5. "loader_path" this is esignet mount volume from where all the runtime dependencies are loaded to classpath. If any new esignet plugins to be tested
should be placed in this folder and respective plugin configuration should be updated in config/esignet-default.properties.

```Note: Refer https://docs.esignet.io/integration to know how to create custom plugins to integrate.```

## How to run this setup?

1. Create loader_path folder in the same directory and Download the eisgnet mock plugin from [here](https://repo1.maven.org/maven2/io/mosip/esignet/mock/mock-esignet-integration-impl/0.9.2/mock-esignet-integration-impl-0.9.2.jar) 
and copy the downloaded jar under loader_path directory.

2. Start the docker-compose file

> docker-compose up

3. Download the postman script from [here](../docs/postman-collections/esignet-with-mock-IDA.postman_collection.json)
and its environment from [here](../docs/postman-collections/esignet-with-mock-IDA.postman_environment.json)

4. Import the downloaded collection and environment into postman.

5. To create an OIDC/OAuth client, run the below request from the postman collection "OIDC Client mgmt" folder
   * Get CSRF token
   * Create OIDC Client

6. To Create a Mock identity, run the below request from the postman collection "Mock-Identity-System" folder
   * Create Mock Identity

7. To run the OIDC flow with mock identity run the below request(same order) from the postman collection "AuthCode flow with OTP login" folder.
   * Get CSRF token
   * Authorize / OAuthdetails request
   * Send OTP
   * Authenticate User
   * Authorization Code
   * Get Tokens
   * Get userInfo

8. To run the Verifiable Credential Issuance flow with mock identity run the below request(same order) from the postman collection "VCI" folder.
   * Get CSRF token
   * Authorize / OAuthdetails request
   * Send OTP
   * Authenticate User
   * Authorization Code
   * Get Tokens
   * Get Credential


## How to Access esignet UI?

To invoke the authorize endpoint of esignet UI to start OIDC/VCI flow, use the below URL:

http://localhost:3000/authorize?nonce=ere973eieljznge2311&state=eree2311&client_id=health-service-client&redirect_uri=https://healthservices.com/callback&scope=openid&response_type=code&acr_values=mosip:idp:acr:generated-code&claims=%7B%22userinfo%22:%7B%22name%22:%7B%22essential%22:false%7D,%22phone_number%22:%7B%22essential%22:true%7D%7D,%22id_token%22:%7B%7D%7D&claims_locales=en&display=page&state=consent&ui_locales=en-IN

```Note: Change the value of client_id, redirect_uri, acr_values and claims as per your requirement in the above URL.```

