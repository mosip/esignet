@client-mgmt
Feature: Client management — create field validation (negative)
  Ported from the Java api-test rig (v1.8.0): an otherwise-valid create request
  with a single bad field surfaces a specific error code (not generic
  invalid_input). Verified against the live mock, 2026-07-21. All rejected —
  no client is created (response: null).

  Background:
    Given I authenticate as admin
    And a fresh request timestamp

  Scenario: Create rejects an invalid public key type
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "bdd-neg-pubkey",
          "clientName": "bdd pubkey",
          "clientNameLangMap": { "eng": "bdd pubkey" },
          "relyingPartyId": "bdd-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": { "kty": "invalid", "e": "AQAB", "n": "abc" },
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_public_key"

  Scenario: Create rejects an invalid additionalConfig value
    Given a generated RSA public key as "pubjwk"
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "bdd-neg-addlcfg",
          "clientName": "bdd addlcfg",
          "clientNameLangMap": { "eng": "bdd addlcfg" },
          "relyingPartyId": "bdd-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "userinfo_response_type": "INVALID" }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
