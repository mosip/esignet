## Identity Provider Service

* Management - Endpoints for creation and updation of OIDC client details
* OIDC - All OIDC compliant endpoints for performing the Open ID Connect flows
* UI - All endpoints used by the UI application
* Wallet-app - All endpoints used by wallet-app

![](/docs/IdP-service-basic-flow.png)

## Caching details

IdP-UI transaction

| Endpoint    | Cache                              | Evict                              |
|-------------|------------------------------------|------------------------------------|
| oauthDetails | preauthsessions (k: transactionId) |                                    |
| authenticate | authenticated (k: transactionId)   | preauthsessions (k: transactionId) |
| authCode    | consented (k: codeHash)            | authenticated (k: transactionId)   |
| token       | kyc   (k: accessTokenHash)         | consented  (k: codeHash)           |


Linked transactions

| Endpoint        | Cache                                                                 | Evict                                                              |
|-----------------|-----------------------------------------------------------------------|--------------------------------------------------------------------|
| oauthDetails    | preauthsessions (k: transactionId)                                    |                                                                    |
| linkTransaction | linkedsessions (k: linkTransactionId), linkcodehash (k: linkCodeHash) | preauthsessions (k: transactionId)                                 |
| authenticate    | linkedauth (k: linkTransactionId), linkcodehash (k: linkCodeHash)     | linkedsessions (k: linkTransactionId)                                                                |
| authCode        | consented (k: codeHash), linkcodehash (k: linkCodeHash)               | linkedauth (k: linkTransactionId) |
| token           | kyc  (k: accessTokenHash)                                             | consented  (k: codeHash), linkcodehash (k: linkCodeHash)                                           |


## Databases
Refer to [SQL scripts](db_scripts/mosip_idp).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).
