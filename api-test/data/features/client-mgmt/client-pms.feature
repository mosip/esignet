@client-mgmt-pms
Feature: POST/PUT {pms}/oauth/client — client management via PMS (mosipid)
  The mosipid counterpart to the plugin-independent client-mgmt feature. Real IDA
  auth needs the OIDC client bound to an onboarded partner + published policy, and
  that binding only exists when the client is registered through
  partner-management-service (PMS) /oauth/client — not eSignet client-mgmt
  directly. PMS writes authPartnerId as the client's relying-party id and derives
  the clientId from the public key (so we read it back), then persists the client
  into eSignet, where we verify it via client-mgmt GET.

  # Runs only for MOSIP_ESIGNET_AUTHN_PROVIDER=mosip with PMS_BASE_URL/AUTH_PARTNER_ID/
  # AUTH_POLICY_ID configured (gated by the @client-mgmt-pms tag + the guard
  # step). userClaims/authContextRefs are NOT sent at registration for mosipid —
  # they are governed by the policy (policyId). PMS reuses the same Keycloak
  # credentials as client-mgmt; for mosipid the operator supplies the
  # partner-client secret in KEYCLOAK_*.

  Background:
    Given I authenticate as admin
    And PMS registration is configured
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"

  Scenario: Create a client via PMS returns a generated clientId, persisted in eSignet
    When I send a "POST" request to "{{pms_base}}/oauth/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "name": "api pms create",
          "clientNameLangMap": { "eng": "api pms create" },
          "authPartnerId": "{{auth_partner_id}}",
          "policyId": "{{policy_id}}",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON path "response.clientId" should exist
    And I save the JSON value at "response.clientId" as "cid"

    # read it back from eSignet — PMS persisted it there under the generated id
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

  Scenario: Update a client via PMS changes its name and redirect URIs
    When I send a "POST" request to "{{pms_base}}/oauth/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "name": "api pms update",
          "clientNameLangMap": { "eng": "api pms update" },
          "authPartnerId": "{{auth_partner_id}}",
          "policyId": "{{policy_id}}",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And I save the JSON value at "response.clientId" as "cid"

    # PMS update is a PUT to /oauth/client/{client_id} (ClientDetailUpdateRequestV2);
    # publicKey/authPartnerId/policyId are fixed at create and not sent here
    When I send a "PUT" request to "{{pms_base}}/oauth/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api pms updated",
          "clientNameLangMap": { "eng": "api pms updated" },
          "status": "ACTIVE",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb", "https://example.org/cb2"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON path "errors" should be null

    # The GET response exposes only clientId and status, so the new name is not
    # observable there — the PUT accepting it (errors null) is the update
    # evidence; this only confirms the client is still retrievable and ACTIVE.
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

  Scenario: Deactivate then reactivate a client via PMS update
    When I send a "POST" request to "{{pms_base}}/oauth/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "name": "api pms status",
          "clientNameLangMap": { "eng": "api pms status" },
          "authPartnerId": "{{auth_partner_id}}",
          "policyId": "{{policy_id}}",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And I save the JSON value at "response.clientId" as "cid"

    # deactivate — status is normalized to INACTIVE
    When I send a "PUT" request to "{{pms_base}}/oauth/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api pms status",
          "clientNameLangMap": { "eng": "api pms status" },
          "status": "INACTIVE",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

    # reactivate
    When I send a "PUT" request to "{{pms_base}}/oauth/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api pms status",
          "clientNameLangMap": { "eng": "api pms status" },
          "status": "ACTIVE",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"
