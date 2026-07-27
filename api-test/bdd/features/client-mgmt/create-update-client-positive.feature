@client-mgmt
Feature: Client management — create + get + update (positive, happy path)
  The positive counterpart to the create/get/update negative features: valid
  requests register a client, read it back, and update it cleanly. client-mgmt is
  admin-authenticated (Keycloak bearer) and plugin-independent — the same PMS
  endpoint serves the mock, sunbird and mosip plugins — so these run unchanged
  under any AUTHN_PROVIDER and are the create/get/update checklist to green before
  standing up a new identity plugin (e.g. mosipid).

  # eSignet returns HTTP 200 with "response" populated and "errors": null on
  # success (MOSIP API convention). The response object only exposes clientId and
  # status; create fixes status to ACTIVE, update normalizes active/inactive to
  # ACTIVE/INACTIVE (case-insensitive). A fresh, per-run clientId keeps every
  # create repeatable — a duplicate clientId would be rejected. authContextRefs
  # uses static-code, which every plugin's acr mapping accepts at registration
  # time; login-flow acr selection is exercised separately by the e2e surface.

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
          "clientName": "bdd positive create",
          "clientNameLangMap": { "eng": "bdd positive create" },
          "relyingPartyId": "bdd-e2e-rp",
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

  Scenario: Get a newly created client returns its clientId and status
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd positive get",
          "clientNameLangMap": { "eng": "bdd positive get" },
          "relyingPartyId": "bdd-e2e-rp",
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

  Scenario: Update a client's name and redirect URIs
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd positive update",
          "clientNameLangMap": { "eng": "bdd positive update" },
          "relyingPartyId": "bdd-e2e-rp",
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

    # update it (new name + an extra redirect uri) — client-mgmt update is a PUT
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "bdd positive updated",
          "clientNameLangMap": { "eng": "bdd positive updated" },
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

  Scenario: Deactivate then reactivate a client via update
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "bdd positive status",
          "clientNameLangMap": { "eng": "bdd positive status" },
          "relyingPartyId": "bdd-e2e-rp",
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

    # deactivate — status is accepted case-insensitively and normalized to INACTIVE
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "bdd positive status",
          "clientNameLangMap": { "eng": "bdd positive status" },
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
          "clientName": "bdd positive status",
          "clientNameLangMap": { "eng": "bdd positive status" },
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
