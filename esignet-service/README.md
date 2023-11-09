## e-Signet Service

* AuthorizationController - All the endpoints used by oidc-ui to begin the OIDC transaction, authenticate, take consent and generate auth-code.
* ClientManagementController - Endpoints to create/update OIDC clients
* LinkedAuthorizationController - Endpoints to shift browser transaction to wallet app, perform linked auth and linked consent.
* OAuthController - Endpoints specific to OAUTH spec like /token and /.well-known/jwks.json
* OpenIdController - Endpoints specific to OIDC protocol like /userinfo and /.well-known/openid-configuration
* SystemInfoController - Endpoints to get the pet public part of the keys managed in the keystore by keymanager.
* KeyBindingController - Endpoints used by wallets to bind a key to an individual ID to support wallet local authentication.
* VCIController - Wallet initiated /credential endpoint returning just in time credential and /.well-known/openid-credential-issuer endpoint specific to [OpenID4VCI specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)

Note: VCI implementations currently only supports ldp_vc format with 'jwt' PoP. And we only issue scope based VC. 
Both mock plugin and the MOSIP IDA plugin supports only scoped based VC issuance.

## e-Signet Plugins
1. We have well-defined plugin interfaces in esignet-intergration-api. 
2. Mock plugin implementations and the MOSIP specific plugin implementations are available.
3. Check the below URL for more details:

 > https://github.com/mosip/esignet-mock-services/tree/master/mock-esignet-integration-impl

 > https://github.com/mosip/id-authentication/tree/master/authentication/esignet-integration-impl

4. All the required plugins are runtime dependency to esignet-service.

![](/docs/esignet-service-basic-interations.png)

## Local setup of e-Signet with mock plugins

1. Create database mosip_esignet.
2. Run all the scripts under db_scripts/mosip_esignet/ddl folder.
3. Run the below insert statements in mosip_esignet database:

   > INSERT INTO KEY_POLICY_DEF(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES ('ROOT', 1095, 50, 'NA', true, 'mosipadmin', now());

   > INSERT INTO KEY_POLICY_DEF(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES ('OIDC_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now());

   > INSERT INTO KEY_POLICY_DEF(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES ('OIDC_PARTNER', 1095, 50, 'NA', true, 'mosipadmin', now());

   > INSERT INTO KEY_POLICY_DEF(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES ('BINDING_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now());

   > INSERT INTO KEY_POLICY_DEF(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES ('MOCK_BINDING_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now());
   
4. Build the plugin jar from below repo and add the built plugin jar as runtime dependency in esignet-service
  
   > https://github.com/mosip/esignet-mock-services/tree/master/mock-esignet-integration-impl

5. Build the current esignet repository with the below command:
   
   > mvn clean install -Dgpg.skip=true -DskipTests=true

6. Run the below command to start the esignet-service with mock plugin

   > java -jar -Dloader.path=mock-esignet-integration-impl.jar esignet-service.jar

7. Once the service is up, swagger should be accessible with the below URL

   > http://localhost:8088/v1/esignet/swagger-ui.html

8. Mock plugins connect to mock-identity-system, refer below document to start mock-identity-system in parallel
   
   > https://github.com/mosip/esignet-mock-services/tree/master/mock-identity-system#local-setup-of-mock-identity-system

9. Also find the latest postman collection under "docs/postman-collections" folder with environment json

   Order of execution in postman script for OIDC flow is:
     * Create identity
     * Create OIDC client
     * Authorize / OAuthdetails request
     * Send OTP 
     * Authenticate user
     * Authorization Code
     * Get Tokens
     * Get Userinfo


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



## Databases
Refer to [SQL scripts](db_scripts/mosip_esignet).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).
