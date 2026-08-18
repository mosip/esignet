@client-mgmt
Feature: GET /client-mgmt/client/{clientId} — read a client
  Reading a registered client back, and what happens when the id is unknown.

  The read endpoint exposes only clientId and status — not the name, redirect
  URIs or claims that were registered. That is the whole contract, so a positive
  case can assert nothing more, and update evidence has to come from the PUT
  response instead (see update-client.feature).

  # MOSIP API convention: both outcomes are HTTP 200. An unknown id comes back
  # with an "errors" array carrying errorCode "invalid_client_id", not a 404.

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  Scenario: Get a newly created client returns its clientId and ACTIVE status
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api positive get",
          "clientNameLangMap": { "eng": "api positive get" },
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

    # read it back — the registered client is retrievable by its id
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

  Scenario: Get a client that does not exist is reported as not found
    When I send a "GET" request to "/client-mgmt/client/does-not-exist-xyz"
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_id"
    And the JSON path "errors.0.errorMessage" should exist
