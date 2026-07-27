@client-mgmt
Feature: Client management — create (negative/edge)
  eSignet client-mgmt is admin-authenticated (bearer token from Keycloak) and is
  not exercised by the conformance suite. Runs only when KEYCLOAK_* creds are set.

  # eSignet follows the MOSIP API convention: validation failures come back as
  # HTTP 200 with a populated "errors" array and "response": null (NOT a 4xx).

  Background:
    Given I authenticate as admin
    And a fresh request timestamp

  Scenario: Create rejects an empty request
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      { "requestTime": "{{now}}", "request": {} }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"
    And the JSON path "response" should be null

  Scenario: Create rejects a missing clientId
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "bdd no id",
          "relyingPartyId": "bdd-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": { "kty": "RSA", "e": "AQAB", "n": "invalid" },
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  Scenario: Create rejects an invalid redirect URI and public key
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "bdd-negative-client",
          "clientName": "bdd negative",
          "relyingPartyId": "bdd-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["not-a-valid-uri"],
          "publicKey": { "kty": "RSA", "e": "AQAB", "n": "invalid" },
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"
