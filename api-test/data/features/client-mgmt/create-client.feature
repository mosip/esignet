@client-mgmt
Feature: POST /client-mgmt/client — register a client
  Everything the create endpoint does, success and rejection, in one place: a
  valid request registers a client, and each single-bad-field request surfaces
  the specific error code for that field.

  client-mgmt is admin-authenticated (Keycloak bearer) and plugin-independent —
  the same /client-mgmt/client endpoint serves the mock, sunbird and mosip
  plugins, so these run unchanged under any AUTHN_PROVIDER. (PMS /oauth/client
  is the mosipid-specific alternative, covered in client-pms.feature.) The
  endpoint is not exercised by the conformance suite, and runs only when the
  KEYCLOAK_* admin credentials are set.

  # eSignet follows the MOSIP API convention: BOTH outcomes are HTTP 200. Success
  # populates "response" with "errors": null; a validation failure populates the
  # "errors" array with "response": null. Never a 4xx.
  #
  # A fresh, per-scenario clientId keeps every create repeatable — a duplicate
  # clientId would be rejected. authContextRefs uses static-code, which every
  # plugin's acr mapping accepts at registration time; login-flow acr selection is
  # exercised separately by the e2e surface.

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  Scenario: Create a client returns the clientId and ACTIVE status
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api positive create",
          "clientNameLangMap": { "eng": "api positive create" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name", "email"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

  Scenario: Create rejects an empty request
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      { "requestTime": "{{now}}", "request": {} }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"
    And the JSON path "response" should be null

  # Only clientId is missing; every other field is valid. With a second bad field
  # in the same body the outcome would depend on the server's validation order.
  Scenario: Create rejects a missing clientId
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api no id",
          "relyingPartyId": "api-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  # Only the redirect URI is malformed; the public key is the valid generated one,
  # so this cannot collide with the invalid_public_key case below.
  Scenario: Create rejects an invalid redirect URI
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api negative",
          "relyingPartyId": "api-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["not-a-valid-uri"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  # The remaining two are field-specific: an otherwise-valid request with one bad
  # field surfaces that field's own error code, not a generic invalid_input.
  # Ported from the Java api-test rig (v1.8.0), verified against the live mock.
  Scenario: Create rejects an invalid public key type
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api pubkey",
          "clientNameLangMap": { "eng": "api pubkey" },
          "relyingPartyId": "api-rp",
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
    And the JSON path "response" should be null

  Scenario: Create rejects an invalid additionalConfig value
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg",
          "clientNameLangMap": { "eng": "api addlcfg" },
          "relyingPartyId": "api-rp",
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
    And the JSON path "response" should be null
