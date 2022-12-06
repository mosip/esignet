## Identity Provider Service

* Management - Endpoints for creation and updation of OIDC client details
* OIDC - All OIDC compliant endpoints for performing the Open ID Connect flows
* UI - All endpoints used by the UI application
* Wallet-app - All endpoints used by wallet-app

![](/docs/IdP-service-basic-flow.png)

## Caching details

IdP-UI transaction

| Endpoint     | Cache                                               | Evict                                               |
|--------------|-----------------------------------------------------|-----------------------------------------------------|
| oauthDetails | preauth (k: transactionId, v: IdPTransaction)       |                                                     |
| authenticate | authenticated (k: transactionId, v: IdPTransaction) | preauth (k: transactionId, v: IdPTransaction)       |
| authCode     | authcodegenerated (k: codeHash, v: IdPTransaction)  | authenticated (k: transactionId, v: IdPTransaction) |
| token        | userinfo   (k: accessTokenHash, v: IdPTransaction)  | authcodegenerated  (k: codeHash, v: IdPTransaction)         |
| userinfo     |                                                     |                                                     |


Linked transactions

| Endpoint        | Cache                                                                                                   | Evict                                                                                                                                                               | Kafka                             |
|-----------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| oauthDetails    | preauth (k: transactionId, v: IdPTransaction)                                                           |                                                                                                                                                                     |                                   |
| generateLinkCode| linkcodegenerated (k: linkCodeHash, v: LinkTransactionMetadata)                                         |                                                                                                                                                                     |                                   |
| linkTransaction | linked (k: linkTransactionId, v: IdPTransaction), linkedcode (k: linkCodeHash, v: LinkTransactionMetadata) | preauth (k: transactionId, v: IdPTransaction) , linkcodegenerated (k: linkCodeHash, v: LinkTransactionMetadata)                                                     | topic: linked, v: linkcodehash    |
| linkStatus |                                                                                                         |                                                                                                                                                                     |
| authenticate    | linkedauth (k: linkTransactionId, v: IdPTransaction)                                                    | linked (k: linkTransactionId, v: IdPTransaction)                                                                                                                    |                                   |
| saveConsent     | consented (k: linkedTransactionId, v: IdPTransaction)                                                   | linkedauth (k: linkTransactionId, v: IdPTransaction)                                                                                                                | topic: consented, v: linkTransactionId |
 | linkAuthCode | authcodegenerated (k: codeHash, v: IdPTransaction)                                                    |                                                                                                                                                                     ||
| token           | userinfo  (k: accessTokenHash)                                                                          | authcodegenerated (k: codeHash, v: IdPTransaction), consented (k: linkedTransactionId, v: IdPTransaction), linkedcode (k: linkCodeHash, v: LinkTransactionMetadata) |                                   |
| userinfo |                                                                                                         |                                                                                                                                                                     |



## Databases
Refer to [SQL scripts](db_scripts/mosip_idp).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).
