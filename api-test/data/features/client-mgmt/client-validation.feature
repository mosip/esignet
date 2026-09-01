@client-mgmt
Feature: Client field validation — the rejection paths for each list field
  create-client.feature and update-client.feature establish that a well-formed
  client is accepted. This file covers the other direction for the five list
  fields — redirectUris, userClaims, authContextRefs, grantTypes and
  clientAuthMethods — where each list is checked for size, for duplicates, and
  for whether its values are in the allowed set.

  The three write paths do NOT apply the same size rules, which is the reason
  several scenarios below only make sense on one of them:

    call site   minimum                       maximum
    create      redirectUris >= 1             none
    update      every list >= 1               none
    patch       none                          redirectUris 5, userClaims 30,
                                              authContextRefs 30, grantTypes 3,
                                              clientAuthMethods 3

  So the "must not be empty" rules are only reachable through create and update,
  and the size ceilings are only reachable through PATCH. Neither set can be
  reached from the other path, which is why the ceilings live in their own
  scenario at the bottom rather than alongside the create cases.

  Checks run in a fixed order — size, then uniqueness, then allowed values — and
  each field reports its own error code, so a rejection names the field that
  caused it rather than a generic failure.

  # MOSIP API convention: both outcomes are HTTP 200. A rejection populates
  # "errors" and nulls "response".

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  # ------------------------------------------------------ redirect URIs --

  # A redirect URI is where the user is sent back to with a code, so a malformed
  # or wildcarded one is a security question, not a formatting one: wildcards are
  # permitted inside a path segment but never in the scheme or host, or an
  # attacker-controlled host could match.
  Scenario: Create rejects redirect URI lists that are empty, duplicated or malformed
    # empty — create requires at least one
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej uri", "clientNameLangMap": { "eng": "api rej uri" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": [],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_redirect_uri"

    # the same URI twice
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej uri", "clientNameLangMap": { "eng": "api rej uri" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb", "https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_redirect_uri"

    # no scheme and no host — not a resolvable callback
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej uri", "clientNameLangMap": { "eng": "api rej uri" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["not-a-uri"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_redirect_uri"

    # wildcard in the host — the case the path-segment allowance must not admit
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej uri", "clientNameLangMap": { "eng": "api rej uri" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://*.example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_redirect_uri"

  # ------------------------------------------------- claims and ACR values --

  # userClaims and authContextRefs are closed sets. An unrecognised entry is
  # rejected rather than stored and ignored, so a typo surfaces at registration
  # instead of as a silently missing claim at userinfo time.
  Scenario: Create rejects unknown claims and unknown or duplicated ACR values
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej claim", "clientNameLangMap": { "eng": "api rej claim" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}},
          "userClaims": ["name", "not_a_real_claim"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_claim"

    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej acr", "clientNameLangMap": { "eng": "api rej acr" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:not-a-real-acr"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_acr"

    # duplicates are caught before the allowed-value check, so this is the
    # uniqueness rule and not the allow-list rule being exercised
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej acr", "clientNameLangMap": { "eng": "api rej acr" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code", "mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_acr"

  # ------------------------------------------ grant types and auth methods --

  # Both fields currently admit exactly one value each. They are still lists in
  # the wire format, so the duplicate case is reachable and worth pinning.
  Scenario: Create rejects unsupported or duplicated grant types and auth methods
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej grant", "clientNameLangMap": { "eng": "api rej grant" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["client_credentials"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_grant_type"

    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej grant", "clientNameLangMap": { "eng": "api rej grant" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code", "authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_grant_type"

    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej auth", "clientNameLangMap": { "eng": "api rej auth" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["client_secret_basic"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_auth"

    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api rej auth", "clientNameLangMap": { "eng": "api rej auth" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt", "private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_auth"

  # ------------------------------------------------- update: non-empty lists --

  # Update requires every list to carry at least one entry, where create only
  # demands it of redirectUris. The client id below is never registered and does
  # not need to be: update validates the body before it looks the row up, so
  # these are rejections about the payload, not about the client being missing.
  Scenario: Update rejects an empty value for each required list
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api upd empty", "clientNameLangMap": { "eng": "api upd empty" },
          "status": "ACTIVE", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": [],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_claim"

    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api upd empty", "clientNameLangMap": { "eng": "api upd empty" },
          "status": "ACTIVE", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": ["name"],
          "authContextRefs": [],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_acr"

    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api upd empty", "clientNameLangMap": { "eng": "api upd empty" },
          "status": "ACTIVE", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": [], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_grant_type"

    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api upd empty", "clientNameLangMap": { "eng": "api upd empty" },
          "status": "ACTIVE", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": []
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_auth"

  # ------------------------------------------------------ patch: ceilings --

  # The size ceilings exist only on the patch path, so this is the only place
  # they can be exercised at all. Each list below is one item over its limit and
  # otherwise entirely valid — the entries are repeats of a permitted value, so
  # the rejection can only be the size rule and not the allow-list or (where it
  # applies) the uniqueness rule, both of which are checked after it.
  Scenario: Patch enforces the maximum size of each list
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}", "clientName": "api patch ceiling", "clientNameLangMap": { "eng": "api patch ceiling" },
          "relyingPartyId": "api-e2e-rp", "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {{pubjwk}}, "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"], "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200

    # six redirect URIs against a ceiling of five
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "redirectUris": [
            "https://example.org/cb1", "https://example.org/cb2", "https://example.org/cb3",
            "https://example.org/cb4", "https://example.org/cb5", "https://example.org/cb6"
          ]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_redirect_uri"

    # thirty-one claims against a ceiling of thirty
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "userClaims": [
            "name","name","name","name","name","name","name","name","name","name",
            "name","name","name","name","name","name","name","name","name","name",
            "name","name","name","name","name","name","name","name","name","name","name"
          ]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_claim"

    # thirty-one ACR values against a ceiling of thirty
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "authContextRefs": [
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code","mosip:idp:acr:static-code","mosip:idp:acr:static-code",
            "mosip:idp:acr:static-code"
          ]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_acr"

    # four grant types against a ceiling of three
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "grantTypes": ["authorization_code","authorization_code","authorization_code","authorization_code"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_grant_type"

    # four auth methods against a ceiling of three
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientAuthMethods": ["private_key_jwt","private_key_jwt","private_key_jwt","private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_auth"

    # the client is untouched by the five rejections above
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"
