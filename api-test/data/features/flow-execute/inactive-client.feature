@inactive-client
Feature: GET /oauth2/authorize — a client that has been deactivated
  Deactivating a client is the operator's kill switch: it is how a relying party
  that has been retired, compromised or offboarded is taken out of service
  without deleting its record. client-mgmt/client-status.feature proves the
  switch can be thrown and that the INACTIVE status is stored and read back.
  This file asks the question that makes the switch worth anything — whether
  eSignet then acts on it.

  It is asserted at /oauth2/authorize because that is the first door: a client
  that cannot get past it cannot reach login, a code, or a token, whatever the
  later endpoints do. An unknown client id is already refused here
  (authorize.feature), and a deactivated client is the same claim — this id may
  not authorize — so the same rejection is expected.

  The token, PAR and introspection endpoints ask the same question further in,
  but they authenticate the client with a signed private_key_jwt assertion that
  this surface has no way to produce. Those live in the e2e surface instead, as
  the "inactive client" scenarios in data/scenarios/e2e-scenarios*.json.

  Requests are made without following redirects so the 302 and its Location are
  asserted directly, as in authorize.feature. Gated on @inactive-client, which
  needs admin auth: the scenarios register and deactivate a client of their own
  rather than driving FLOW_CLIENT_ID, which is shared and must stay usable.

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  # The whole property in one scenario, and it is asserted in both directions:
  # the same request is made before and after the deactivation, so a rejection
  # afterwards can only be the status. Without the first call a failure to
  # authorize could just as well be a malformed request or a bad client.
  Scenario: A deactivated client can no longer start an authorization
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api inactive authz",
          "clientNameLangMap": { "eng": "api inactive authz" },
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

    # while ACTIVE: authorize is accepted and hands off to the login UI
    When I send a "GET" request to "{{authz_endpoint}}?response_type=code&client_id={{cid}}&scope=openid&redirect_uri=https://example.org/cb&state=s1&nonce=n1&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256" without following redirects
    Then the response status should be 302
    And the redirect location should contain "{{cid}}"

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "INACTIVE" }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

    # the status eSignet itself now stores for this client — read back so a
    # failure below cannot be blamed on a write that never landed
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

    # while INACTIVE: the identical request must now be refused, the same way an
    # unknown client id is (authorize.feature). Reaching the login UI here means
    # a retired or compromised client can still start authorizations.
    When I send a "GET" request to "{{authz_endpoint}}?response_type=code&client_id={{cid}}&scope=openid&redirect_uri=https://example.org/cb&state=s1&nonce=n1&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256" without following redirects
    Then the response status should be 302
    And the redirect location should contain "/error"

  # The positive control for the scenario above. If reactivation did not restore
  # authorization, the negative would be proving something about a broken client
  # rather than about its status.
  Scenario: Reactivating a client restores its ability to authorize
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api reactivated authz",
          "clientNameLangMap": { "eng": "api reactivated authz" },
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
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "INACTIVE" }
      }
      """
    Then the response status should be 200
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "ACTIVE" }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"

    When I send a "GET" request to "{{authz_endpoint}}?response_type=code&client_id={{cid}}&scope=openid&redirect_uri=https://example.org/cb&state=s1&nonce=n1&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256" without following redirects
    Then the response status should be 302
    And the redirect location should contain "{{cid}}"
