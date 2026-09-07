@client-mgmt
Feature: Client additionalConfig — protocol switches and allowlist validation
  additionalConfig is the only place a client turns on the protocol hardening
  eSignet supports per client: PKCE, pushed authorization requests and
  DPoP-bound access tokens. This file covers what the endpoint accepts and what
  it rejects for those keys, plus the allowlist and the remaining typed keys
  that no other feature file touches.

  Consent-specific keys (consent_expire_in_mins, purpose) live in
  consent-config.feature; userinfo_response_type has its rejection case in
  create-client.feature. Everything else in the allowlist is here.

  # The three protocol switches are read back by the engine's actor provider and
  # become OAuthClient.PKCERequired / RequirePushedAuthorizationRequests /
  # DPoPBoundAccessTokens. The e2e surface registers clients with exactly these
  # keys and then drives the flow they enforce; this file is the registration
  # half of that contract, asserted without needing a login.
  #
  # MOSIP API convention throughout: both outcomes are HTTP 200. A rejection
  # populates "errors" and nulls "response".

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  # ---------------------------------------------------------------- positive --

  # The exact registration shape the e2e surface uses for its "all three on"
  # combination. If this stops being accepted, every protocol-combination e2e
  # scenario fails at registration rather than at the behaviour it means to test.
  Scenario: Create accepts all three protocol switches together
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg protocol",
          "clientNameLangMap": { "eng": "api addlcfg protocol" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": {
            "require_pkce": true,
            "require_pushed_authorization_requests": true,
            "dpop_bound_access_tokens": true
          }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

  # ------------------------------------------------------- allowlist rejects --

  # validate.go keys additionalConfig against a fixed allowlist, so a plausible
  # misspelling of a real switch is rejected outright rather than silently
  # stored and never enforced — the failure mode that would make a client look
  # hardened while it keeps accepting unprotected requests.
  Scenario: Create rejects an additionalConfig key that is not on the allowlist
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg unknown key",
          "clientNameLangMap": { "eng": "api addlcfg unknown key" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "require_dpop": true }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
    And the JSON path "response" should be null

  # additionalConfig is bound as a raw JSON message, so a non-object reaches the
  # validator rather than failing request binding — it must still be rejected.
  Scenario: Create rejects an additionalConfig that is not a JSON object
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg not object",
          "clientNameLangMap": { "eng": "api addlcfg not object" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": "require_pkce"
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
    And the JSON path "response" should be null

  # --------------------------------------------------- protocol switch typing --

  # Each switch is decoded into a bool. A quoted "true" is the realistic mistake
  # — it looks right in a config file and would otherwise leave the switch off.
  Scenario Outline: Create rejects a non-boolean <key>
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg bool",
          "clientNameLangMap": { "eng": "api addlcfg bool" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "<key>": <value> }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
    And the JSON path "response" should be null

    Examples:
      | key                                   | value    |
      | require_pkce                          | "true"   |
      | require_pushed_authorization_requests | 1        |
      | dpop_bound_access_tokens              | {}       |
      | signup_banner_required                | "yes"    |
      | forgot_pwd_link_required              | ["true"] |

  # ---------------------------------------------------- remaining typed keys --

  # id_token_response_type accepts only JWS or JWE. "JWT" is the near-miss that
  # would otherwise decide whether the ID token comes back signed or encrypted.
  Scenario: Create rejects an id_token_response_type outside JWS/JWE
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg idtoken",
          "clientNameLangMap": { "eng": "api addlcfg idtoken" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "id_token_response_type": "JWT" }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
    And the JSON path "response" should be null

  # allowed_authorization_scopes downscopes what the client may request. It must
  # be a list of distinct, non-blank strings: a duplicate or a blank entry would
  # corrupt the very list that is supposed to narrow the client.
  Scenario Outline: Create rejects an invalid allowed_authorization_scopes: <case>
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg scopes",
          "clientNameLangMap": { "eng": "api addlcfg scopes" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "allowed_authorization_scopes": <value> }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
    And the JSON path "response" should be null

    Examples:
      | case            | value                |
      | not an array    | "openid"             |
      | duplicate entry | ["openid", "openid"] |
      | blank entry     | ["openid", "   "]    |

  # ------------------------------------------------------------- update path --

  # The merged-update path re-validates additionalConfig, so a switch that could
  # not be created cannot be edited in afterwards on a client that registered clean.
  Scenario: Update rejects a non-boolean protocol switch on an existing client
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg update",
          "clientNameLangMap": { "eng": "api addlcfg update" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "require_pkce": true }
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
          "clientName": "api addlcfg update",
          "clientNameLangMap": { "eng": "api addlcfg update" },
          "status": "ACTIVE",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": { "dpop_bound_access_tokens": "true" }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_additional_config"
    And the JSON path "response" should be null

  # Turning the switches on via update is the supported migration path for a
  # client that registered before the deployment enabled PAR/DPoP.
  Scenario: Update accepts turning the protocol switches on
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api addlcfg enable",
          "clientNameLangMap": { "eng": "api addlcfg enable" },
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

    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api addlcfg enable",
          "clientNameLangMap": { "eng": "api addlcfg enable" },
          "status": "ACTIVE",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"],
          "additionalConfig": {
            "require_pkce": true,
            "require_pushed_authorization_requests": true,
            "dpop_bound_access_tokens": true
          }
        }
      }
      """
    # The accepted PUT is the evidence: unlike create, a successful update omits
    # "errors" rather than nulling it, and GET exposes only clientId and status.
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"
