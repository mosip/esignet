@client-mgmt
Feature: Client management — consent configuration (positive + negative)
  Consent is configured per client, in additionalConfig, and validated at
  registration time by clientmgmt's validateAdditionalConfig/validatePurpose:

    consent_expire_in_mins  a NUMBER >= 10 (the consent record's validity period;
                            engine actor_provider reads it as LoginConsent.
                            ValidityPeriod, defaulting to 0 = never expires)
    purpose                 { type (non-empty string, required),
                              title/subTitle (lang maps that MUST carry "@none") }

  These are pure API calls with no login flow, so unlike the consent *behaviour*
  cases (e2e surface: prompt/reuse/deny) they need no session, no identity and no
  captcha — they run unchanged under any AUTHN_PROVIDER.

  eSignet returns HTTP 200 with "errors" populated on a validation reject (MOSIP
  API convention), so every negative here asserts 200 + an errorCode, not a 4xx.

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  Scenario: Create accepts a valid consent expiry and purpose
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd consent config",
          "clientNameLangMap": { "eng": "bdd consent config" },
          "relyingPartyId": "bdd-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name", "email"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": {
            "consent_expire_in_mins": 30,
            "purpose": {
              "type": "verify",
              "title": { "@none": "Verify your identity" },
              "subTitle": { "@none": "Share the listed attributes" }
            }
          }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

  Scenario: Create rejects a consent expiry below the 10-minute minimum
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd consent expiry low",
          "clientNameLangMap": { "eng": "bdd consent expiry low" },
          "relyingPartyId": "bdd-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "consent_expire_in_mins": 5 }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"

  Scenario: Create rejects a non-numeric consent expiry
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd consent expiry type",
          "clientNameLangMap": { "eng": "bdd consent expiry type" },
          "relyingPartyId": "bdd-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "consent_expire_in_mins": "30" }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"

  Scenario: Create rejects a consent purpose with no type
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd purpose no type",
          "clientNameLangMap": { "eng": "bdd purpose no type" },
          "relyingPartyId": "bdd-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "purpose": { "title": { "@none": "No type here" } } }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"

  Scenario: Create rejects a consent purpose with an empty type
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd purpose empty type",
          "clientNameLangMap": { "eng": "bdd purpose empty type" },
          "relyingPartyId": "bdd-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "purpose": { "type": "" } }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"

  # Update re-validates additionalConfig (validate.go calls
  # validateAdditionalConfig on the create, update and merged-update paths), so a
  # consent expiry create rejects must not slip in through a later edit.
  Scenario: Update rejects a consent expiry below the minimum on an existing client
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd consent update",
          "clientNameLangMap": { "eng": "bdd consent update" },
          "relyingPartyId": "bdd-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "consent_expire_in_mins": 30 }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"

    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "bdd consent update",
          "clientNameLangMap": { "eng": "bdd consent update" },
          "status": "ACTIVE",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "consent_expire_in_mins": 5 }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"

  # The lang map must carry the "@none" default entry: the consent UI falls back
  # to it for any locale it has no translation for, so a map without it would
  # render an empty purpose title.
  Scenario: Create rejects a consent purpose title with no @none default
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd purpose no none",
          "clientNameLangMap": { "eng": "bdd purpose no none" },
          "relyingPartyId": "bdd-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "purpose": { "type": "verify", "title": { "eng": "Only english" } } }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
