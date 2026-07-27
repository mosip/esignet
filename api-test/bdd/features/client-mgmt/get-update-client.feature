@client-mgmt
Feature: Client management — get and update (negative/edge)
  Read/update of a client that does not exist. eSignet returns HTTP 200 with an
  "errors" array (MOSIP convention), errorCode "invalid_client_id".

  Background:
    Given I authenticate as admin
    And a fresh request timestamp

  Scenario: Get a client that does not exist is reported as not found
    When I send a "GET" request to "/client-mgmt/client/does-not-exist-xyz"
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_id"
    And the JSON path "errors.0.errorMessage" should exist

  Scenario: Update a client that does not exist is reported as not found
    When I send a "PUT" request to "/client-mgmt/client/does-not-exist-xyz" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "bdd update missing",
          "clientNameLangMap": { "eng": "bdd update missing" },
          "status": "active",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_id"
