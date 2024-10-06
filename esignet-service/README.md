## eSignet Service

* AuthorizationController - All the endpoints used by oidc-ui to begin the OIDC transaction, authenticate, take consent and generate auth-code.
* ClientManagementController - Endpoints to create/update OIDC clients
* LinkedAuthorizationController - Endpoints to shift browser transaction to wallet app, perform linked auth and linked consent.
* OAuthController - Endpoints specific to OAUTH spec like /token and /.well-known/jwks.json
* OpenIdController - Endpoints specific to OIDC protocol like /userinfo and /.well-known/openid-configuration
* SystemInfoController - Endpoints to get the pet public part of the keys managed in the keystore by keymanager.
* KeyBindingController - Endpoints used by wallets to bind a key to an individual ID to support wallet local authentication.

Note: VCI implementations are permanently moved to Inji-Certify.

## eSignet Plugins
1. We have well-defined plugin interfaces in esignet-intergration-api. 
2. Mock plugin implementations and the MOSIP specific plugin implementations are available.
3. Check the below URL for more details:

 > https://github.com/mosip/esignet-plugins/tree/master

 > https://github.com/mosip/digital-credential-plugins

4. All the required plugins are runtime dependency to esignet-service.

![](/docs/esignet-service-basic-interations.png)

## Local setup of eSignet with mock plugins

Kindly check our docker compose setup files to run eSignet locally [here](../docker-compose)

## Caching details

UI transaction

| Endpoint     | Cache                                               | Evict                                               |
|--------------|-----------------------------------------------------|-----------------------------------------------------|
| oauthDetails | preauth (k: transactionId, v: OIDCTransaction)       |                                                     |
| authenticate | authenticated (k: transactionId, v: OIDCTransaction) | preauth (k: transactionId, v: OIDCTransaction)       |
| authCode     | authcodegenerated (k: codeHash, v: OIDCTransaction)  | authenticated (k: transactionId, v: OIDCTransaction) |
| token        | userinfo   (k: accessTokenHash, v: OIDCTransaction)  | authcodegenerated  (k: codeHash, v: OIDCTransaction)         |
| userinfo     |                                                     |                                                     |


Linked transactions

| Endpoint        | Cache                                                                                                   | Evict                                                                                                                                                               | Kafka                             |
|-----------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| oauthDetails    | preauth (k: transactionId, v: OIDCTransaction)                                                           |                                                                                                                                                                     |                                   |
| generateLinkCode| linkcodegenerated (k: linkCodeHash, v: LinkTransactionMetadata)                                         |                                                                                                                                                                     |                                   |
| linkTransaction | linked (k: linkTransactionId, v: OIDCTransaction), linkedcode (k: linkCodeHash, v: LinkTransactionMetadata) | preauth (k: transactionId, v: OIDCTransaction) , linkcodegenerated (k: linkCodeHash, v: LinkTransactionMetadata)                                                     | topic: linked, v: linkcodehash    |
| linkStatus |                                                                                                         |                                                                                                                                                                     |
| authenticate    | linkedauth (k: linkTransactionId, v: OIDCTransaction)                                                    | linked (k: linkTransactionId, v: OIDCTransaction)                                                                                                                    |                                   |
| saveConsent     | consented (k: linkedTransactionId, v: OIDCTransaction)                                                   | linkedauth (k: linkTransactionId, v: OIDCTransaction)                                                                                                                | topic: consented, v: linkTransactionId |
| linkAuthCode | authcodegenerated (k: codeHash, v: OIDCTransaction)                                                    |                                                                                                                                                                     ||
| token           | userinfo  (k: accessTokenHash)                                                                          | authcodegenerated (k: codeHash, v: OIDCTransaction), consented (k: linkedTransactionId, v: OIDCTransaction), linkedcode (k: linkCodeHash, v: LinkTransactionMetadata) |                                   |
| userinfo |                                                                                                         |                                                                                                                                                                     |


eKYC verification transaction

| Endpoint                               | Cache                                                | Evict                                                |
|----------------------------------------|------------------------------------------------------|------------------------------------------------------|
| oauthDetails                           | preauth (k: transactionId, v: OIDCTransaction)       |                                                      |
| authenticate                           | authenticated (k: transactionId, v: OIDCTransaction) | preauth (k: transactionId, v: OIDCTransaction)       |
| claim-details(limited to 1 invocation) | authenticated (k: transactionId, v: OIDCTransaction) |                                                      |
| prepare-signup-redirect                | halted (k: transactionId, v: OIDCTransaction)        | authenticated (k: transactionId, v: OIDCTransaction) |
| complete-signup-redirect               | authenticated (k: transactionId, v: OIDCTransaction) | halted (k: transactionId, v: OIDCTransaction)        |
| authCode                               | authcodegenerated (k: codeHash, v: OIDCTransaction)  | authenticated (k: transactionId, v: OIDCTransaction) |
| token                                  | userinfo   (k: accessTokenHash, v: OIDCTransaction)  | authcodegenerated  (k: codeHash, v: OIDCTransaction) |
| userinfo                               |                                                      |                                                      |


## API document

eSignet API documentation can be found [here](../docs/esignet-openapi.yaml)

## Databases
Refer to [SQL scripts](db_scripts/mosip_esignet).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).
