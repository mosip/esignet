@client-mgmt
Feature: PUT /client-mgmt/client/{clientId} — update a client
  Updating a registered client's name, redirect URIs and status, and what happens
  when the id is unknown.

  Update accepts status case-insensitively and normalizes it to ACTIVE/INACTIVE,
  which is how a client is deactivated and brought back. Note the PUT body has no
  clientId or publicKey: the id comes from the path and the key is immutable.

  # MOSIP API convention: both outcomes are HTTP 200. An unknown id comes back
  # with an "errors" array carrying errorCode "invalid_client_id", not a 404.

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  Scenario: Update a client's name and redirect URIs
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api positive update",
          "clientNameLangMap": { "eng": "api positive update" },
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

    # new name + an extra redirect uri; the accepted PUT is the update evidence,
    # since the GET response exposes only clientId and status.
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api positive updated",
          "clientNameLangMap": { "eng": "api positive updated" },
          "status": "ACTIVE",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb", "https://example.org/cb2"],
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

    # confirm the client is still retrievable and ACTIVE after the update
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

  Scenario: Deactivate then reactivate a client via update
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api positive status",
          "clientNameLangMap": { "eng": "api positive status" },
          "relyingPartyId": "api-e2e-rp",
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
    And the JSON value at "response.status" should be "ACTIVE"

    # deactivate — lowercase "inactive" is accepted and normalized to INACTIVE
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api positive status",
          "clientNameLangMap": { "eng": "api positive status" },
          "status": "inactive",
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
    And the JSON value at "response.status" should be "INACTIVE"

    # reactivate
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api positive status",
          "clientNameLangMap": { "eng": "api positive status" },
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
    And the JSON value at "response.status" should be "ACTIVE"

  # {{cid}}, not a literal: the Background mints a fresh id per scenario and this
  # one never registers it, so the id is unknown even on a long-lived deployment.
  Scenario: Update a client that does not exist is reported as not found
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api update missing",
          "clientNameLangMap": { "eng": "api update missing" },
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
