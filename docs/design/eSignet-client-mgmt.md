# eSignet Client Management API v3

## Overview

Version 3 of the Client Management API introduces support for a new field: `additionalConfig`. This is an optional field that enables clients to provide additional configuration 
to customize eSignet according to their requirements.

The additionalConfig is validated using a JSON schema. The url for the JSON schema can be configured with the `mosip.esignet.additional-config.schema.url`. The default schema is the
`additional_config_request_schema.json` file in the classpath.

The following fields are there currently in additionalConfig

**userinfo_response_type**
: Available options for this field are **JWS** and **JWE**. Same is sent to the ID System in kyc-exchange where response is expected in that format. 

**purpose** - [Documentation]()

**signup_banner_required**
: This boolean field is for showing "Signup with Unified Portal" banner at the bottom of login page.

**forgot_pwd_link_required**
: This boolean field is for giving "Forgot password" option to user on password login page.

**consent_expire_in_mins** - [Documentation](esignet-consent.md)



**Example value for this field**

```json
{
  "userinfo_response_type": "JWS",
  "purpose": {
    "type": "verify",
    "title": {
      "@none": "title"
    },
    "subTitle": {
      "@none": "subtitle"
    }
  },
  "signup_banner_required": true,
  "forgot_pwd_link_required": true,
  "consent_expire_in_mins": 20
}
```
