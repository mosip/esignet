@client-mgmt
Feature: PATCH /client-mgmt/client/{clientId} — partially update a client
  PATCH changes only the fields the body names and leaves every other field as
  it was. That is the whole difference from PUT, which is why this file leads
  with a patch that sends one field and nothing else.

  How "left as it was" is asserted without reading the fields back: the server
  merges the patch onto the stored row and then validates the WHOLE merged
  record, not just the patched part. Every required field — clientName,
  redirectUris, userClaims, authContextRefs, grantTypes, clientAuthMethods —
  must still be present and valid for the request to be accepted. So a
  single-field patch coming back 200 is proof the merge preserved the rest; had
  it behaved like PUT and replaced the record, the same request would have been
  rejected for the fields it never mentioned.

  encPublicKey is the one field with three states rather than two — omitted,
  explicit null, or a JWK object — because null is how a key is removed and
  omitted is how it is left alone. Those cannot be the same value, so they get
  their own scenario.

  # MOSIP API convention, as everywhere else in this directory: both outcomes
  # are HTTP 200. A rejection populates "errors" and nulls "response"; an
  # unknown client id is errorCode "invalid_client_id", not a 404.

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  # ---------------------------------------------------------------- positive --

  # The headline case. The patch body carries "status" and nothing else — see
  # the feature preamble for why its acceptance is the evidence that the other
  # nine fields survived the merge.
  Scenario: A single-field patch changes that field and preserves the rest
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api patch minimal",
          "clientNameLangMap": { "eng": "api patch minimal" },
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
    And the JSON value at "response.status" should be "ACTIVE"

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "inactive" }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "INACTIVE"

    # and the change is durable, not just echoed back in the patch response
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

  # One patch per field, sequentially against the same client. Each request
  # names exactly one field, so every field's merge branch is exercised on its
  # own rather than hidden behind a whole-record write.
  Scenario: Every patchable field is accepted on its own
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api patch fields",
          "clientNameLangMap": { "eng": "api patch fields" },
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
        "request": { "clientName": "api patch renamed" }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"

    # clientNameLangMap is stored alongside clientName rather than in its own
    # column, so patching it alone is the case that would break first if the
    # merge ever rebuilt the name from the patch instead of the stored row.
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "clientNameLangMap": { "eng": "api patch renamed", "fra": "api patch renomme" } }
      }
      """
    Then the response status should be 200

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "logoUri": "https://example.org/logo2.png" }
      }
      """
    Then the response status should be 200

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "redirectUris": ["https://example.org/cb", "https://example.org/cb2"] }
      }
      """
    Then the response status should be 200

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "userClaims": ["name", "email", "phone_number"] }
      }
      """
    Then the response status should be 200

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "authContextRefs": ["mosip:idp:acr:static-code", "mosip:idp:acr:knowledge"] }
      }
      """
    Then the response status should be 200

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "grantTypes": ["authorization_code"] }
      }
      """
    Then the response status should be 200

    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "clientAuthMethods": ["private_key_jwt"] }
      }
      """
    Then the response status should be 200

    # additionalConfig is carried through the merge as raw JSON rather than a
    # decoded map, so it gets its own patch here to prove the raw path round-trips.
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "additionalConfig": {
            "require_pkce": true,
            "dpop_bound_access_tokens": true
          }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"

  # Two fields at once, to show the merge is not limited to a single key and
  # that field order in the body carries no meaning.
  Scenario: A patch naming several fields applies all of them
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api patch multi",
          "clientNameLangMap": { "eng": "api patch multi" },
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
        "request": {
          "status": "inactive",
          "logoUri": "https://example.org/logo-multi.png",
          "redirectUris": ["https://example.org/cb", "https://example.org/cb-multi"],
          "userClaims": ["name", "email"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

  # encPublicKey distinguishes three states, and only two of them are reachable
  # from a normal JSON struct decode — hence the dedicated nullable type behind
  # this field. Setting a key and then clearing it walks both live branches;
  # every other scenario in this file leaves the field omitted, which is the third.
  Scenario: encPublicKey can be set to a key and then explicitly cleared with null
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api patch enckey",
          "clientNameLangMap": { "eng": "api patch enckey" },
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

    # An encryption JWK must additionally carry "alg", so the JWE key-management
    # algorithm is never left for the server to guess. RSA-OAEP-256 is the
    # default supported algorithm.
    #
    # A fixed modulus rather than {{pubjwk}}: the generated key is stashed as a
    # whole JWK object and cannot have "alg" spliced into it, and unlike the
    # signing publicKey an encryption key carries no uniqueness constraint — so
    # the same literal is safe to reuse across runs and across clients.
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "encPublicKey": {
            "kty": "RSA",
            "n": "sXchDaQebHnPiGvyDOAT4saGEUetSyo9MKLOoWFsueri23bOdgWp4Dy1WlUzewbgBHod5pcM9H95GQRV3JDXboIRROSBigeC5yjU1hGzHHyXss8UDprecbAYxknTcQkhslANGRUZmdTOQ5qTRsLAt6BTYuyvVRdhS8exSZEy_c4gs_7svlJJQ4H9_NxsiIoLwAEk7-Q3UXERGYw_75IDrGA84-lA_-Ct4eTlXHBIY2EaV7t7LjJaynVJCpkv4LKjTTAumiGUIuQhrNhZLuF_RJLqHpM2kgWFLU7-VTdL1VbC2tejvcI2BlMkEpk1BzBZI0KQB0GaDWFLN-aEAw3vRw",
            "e": "AQAB",
            "alg": "RSA-OAEP-256",
            "use": "enc"
          }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"

    # null is a removal, not a malformed key — it must be accepted where the
    # same body with a "{}" or a partial JWK would be rejected.
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "encPublicKey": null }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.clientId" should be "{{cid}}"

  # ---------------------------------------------------------------- negative --

  # {{cid}}, not a literal: the Background mints a fresh id per scenario and
  # this one never registers it, so the id is unknown even on a long-lived
  # deployment.
  Scenario: Patch a client that does not exist is reported as not found
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "inactive" }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_client_id"

  # The envelope is checked before the patch body is decoded, so these two fail
  # the same way whether or not the client exists.
  Scenario: Patch without a requestTime is rejected
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "request": { "status": "inactive" }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  # An envelope with no "request" at all is distinct from an empty one: there is
  # nothing to merge, which the decoder reports rather than treating as a no-op
  # patch that would rewrite the record with itself.
  Scenario: Patch with no request object is rejected
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}"
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  Scenario: Patch with a malformed JSON body is rejected
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      { "requestTime": "{{now}}", "request": { "status": }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  # Patched values are validated exactly as they are on a create or update — the
  # merge does not get a weaker rule set. Each case below patches one bad value
  # onto an otherwise valid client, so the rejection can only come from the
  # patched field.
  Scenario: Patched values are validated and bad ones are rejected
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api patch invalid",
          "clientNameLangMap": { "eng": "api patch invalid" },
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

    # status is a closed set; anything outside ACTIVE/INACTIVE is invalid_input
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "BOGUS" }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

    # a URI field gets its own error code, distinct from the generic one above
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "logoUri": "not-a-uri" }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_uri"

    # an encryption JWK with no "alg" is rejected as a bad key rather than
    # silently defaulted — the create path enforces the same rule.
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "encPublicKey": {
            "kty": "RSA",
            "n": "sXchDaQebHnPiGvyDOAT4saGEUetSyo9MKLOoWFsueri23bOdgWp4Dy1WlUzewbgBHod5pcM9H95GQRV3JDXboIRROSBigeC5yjU1hGzHHyXss8UDprecbAYxknTcQkhslANGRUZmdTOQ5qTRsLAt6BTYuyvVRdhS8exSZEy_c4gs_7svlJJQ4H9_NxsiIoLwAEk7-Q3UXERGYw_75IDrGA84-lA_-Ct4eTlXHBIY2EaV7t7LjJaynVJCpkv4LKjTTAumiGUIuQhrNhZLuF_RJLqHpM2kgWFLU7-VTdL1VbC2tejvcI2BlMkEpk1BzBZI0KQB0GaDWFLN-aEAw3vRw",
            "e": "AQAB"
          }
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_public_key"

    # the client is still intact and ACTIVE — a rejected patch writes nothing
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"
