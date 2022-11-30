## Identity Provider Service

IdP OIDC service, provides endpoints to 
1. Generate auth-code 
2. Fetch id-token and access-token
3. Fetch userinfo (KYC)

![](/docs/IdP-service-basic-flow.png)


## Caching details

IdP-UI transaction

| Endpoint    | Cache                              | Evict                              |
|-------------|------------------------------------|------------------------------------|
| oauthDetails | preauthsessions (k: transactionId) |                                    |
| authenticate | authenticated (k: transactionId)   | preauthsessions (k: transactionId) |
| authCode    | consented (k: code)                | authenticated (k: transactionId)   |
| token       | kyc   (k: accessTokenHash)         | consented  (k: code)           |


Linked transactions

| Endpoint           | Cache                                                     | Evict                              |
|-----------------|-----------------------------------------------------------|------------------------------------|
| oauthDetails    | preauthsessions (k: transactionId)                        |                                    |
| linkTransaction | linkedsessions (k: linkTransactionId)                     | preauthsessions (k: transactionId) |
| authenticate    | linkedauth (k: linkTransactionId)                 |                                    |
| authCode        | consented (k: code), linkedconsent (k: linkTransactionId) | linkedauth (k: linkTransactionId), linkedsessions (k: linkTransactionId)    |
| token           | kyc  (k: accessTokenHash)                                 | consented  (k: code)                    |



## TODO Document

generate-link-code    
    link-code -- transactionId
link-transaction
    linkTransactionId -- IdpTransaction --  set in linkedsessions 
                                            set linkTransactionId in linkcodes 
                                            publish linkTransactionId to kafka topic - linkedSessionTopic
send-otp
    linkTransactionId -- IdpTransaction -- check in linkedsessions
authenticate
    linkTransactionId -- IdpTransaction -- check in linkedsessions && set in linkedauth
consent
    linkTransactionId -- IdpTransaction -- check in linkedauth && set in consented

link-status ( i/p -> transactionId, linkCode )
    link-code -- check in linkcodes
                 check linkTransactionId in linkedsessions
    
    
    
    




## Databases
Refer to [SQL scripts](db_scripts/mosip_idp).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).
