# HouseHold ID e-Signet plugin implementation

## About
e-Signet integration with householdID view. Here the householdID DB view plays the role of ID repository. IDA implementation to identify users based on the provided ID number is part of the authenticator plugin.

KYC returned as part of userinfo response will only contain the subject claim. Subject claim will hold the household ID.

Prerequisite:
1. Insert new key alias, in key_alias table, this key is used to sign the KYC
> INSERT INTO KEY_POLICY_DEF(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('HH_AUTHENTICATION_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now());

2. Update below keys in the esignet properties file
> mosip.esignet.integration.scan-base-package=io.mosip.householdid.esignet.integration
>
> mosip.esignet.integration.authenticator=HouseHoldAuthenticationService
>
> mosip.esignet.integration.key-binder=HouseHoldKeyBinder
>
> mosip.esignet.integration.audit-plugin=HouseholdLoggerAuditService

3. Append **'hhidauthsession'** cache name to the list of cache names to be created in esignet properties file
> mosip.esignet.cache.names
>
> mosip.esignet.cache.size
>
> mosip.esignet.cache.expire-in-seconds


4. Add household ID integration plugin into eisgnet classpath

  ```xml
  <groupId>io.mosip.householdid.esignet.integration</groupId>
  <artifactId>household-esignet-integration-impl</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  ```

5. Check for the PWD mapping in amr-acr-mapping.json

```json
{
  "amr" : {
    "PWD" :  [{ "type": "PWD" }],
    "OTP" :  [{ "type": "OTP" }],
    "L1-bio-device" :  [{ "type": "BIO", "count": 1 }]
  },
  "acr_amr" : {
    "mosip:idp:acr:password" : ["PWD"],
    "mosip:idp:acr:generated-code" : ["OTP"],
    "mosip:idp:acr:biometrics" : [ "L1-bio-device" ]
  }
}
```

**Note**: Current version of plugin only supports password based authentication