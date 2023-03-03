## e-Signet Service

* Management - Endpoints for creation and updation of OIDC client details
* OIDC - All OIDC compliant endpoints for performing the Open ID Connect flows
* UI - All endpoints used by the UI application
* Wallet-app - All endpoints used by wallet-app

![](/docs/IdP-service-basic-flow.png)

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
