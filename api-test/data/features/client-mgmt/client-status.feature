@client-mgmt
Feature: Client status — the ACTIVE/INACTIVE lifecycle
  Everything about the status field itself: what sets it, what it accepts, and
  what leaves it alone. Deactivating and reactivating a client through the whole
  update payload is covered in update-client.feature, and a single-field status
  patch in patch-client.feature; this file holds the edges neither reaches.

  Two of them are worth naming up front, because both are easy to get wrong from
  the caller's side and neither announces itself:

    - create has no status field at all. A status sent in a create request is
      silently dropped and the client is registered ACTIVE, so a caller who
      believes they registered a disabled client did not.
    - update is a full replace and status is mandatory in it. A PUT that only
      means to rename a client, and leaves status out, is rejected outright
      rather than preserving the stored value.

  What an INACTIVE client is then still able to DO is a different question, and
  is asked in flow-execute/inactive-client.feature.

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  # CreateClientRequest carries no status member, so this one is dropped during
  # decoding — no invalid_input, no echo of what was asked for. Asserted so a
  # future create that did honour it could not land unnoticed.
  Scenario: Create ignores a status in the request and always registers ACTIVE
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api status create",
          "clientNameLangMap": { "eng": "api status create" },
          "status": "INACTIVE",
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

    # and the drop is durable, not just a response-shaping artifact
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"

  # normalizeStatus upper-cases before matching, so any casing is the same word.
  Scenario Outline: Patch accepts <sent> and stores it as INACTIVE
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api status case",
          "clientNameLangMap": { "eng": "api status case" },
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
        "request": { "status": <sent> }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

    Examples:
      | sent       |
      | "inactive" |
      | "INACTIVE" |
      | "InAcTiVe" |

  # The other side of that closed set. Padding is NOT trimmed — " inactive" is
  # rejected exactly like a misspelling — and neither an empty string nor an
  # explicit null is read as "leave it alone": naming the field at all commits
  # the caller to a valid value.
  Scenario Outline: Patch rejects <case> as a status
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api status bad",
          "clientNameLangMap": { "eng": "api status bad" },
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
        "request": { "status": <sent> }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

    # and the rejected patch changed nothing
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"

    Examples:
      | case                | sent        |
      | leading whitespace  | " inactive" |
      | trailing whitespace | "inactive " |
      | the empty string    | ""          |
      | an explicit null    | null        |
      | a numeric value     | 0           |

  # A patch names the fields it changes and nothing else. Renaming a client that
  # someone deliberately took out of service must not quietly put it back.
  Scenario: Patching another field leaves a deactivated client deactivated
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api status keep",
          "clientNameLangMap": { "eng": "api status keep" },
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

    # a rename, naming no status at all
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "clientName": "api status renamed" }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

  # Repeating a deactivation is a no-op, not a conflict: an operator re-running a
  # deactivation script must not have to care whether it already ran.
  Scenario: Deactivating an already-inactive client is idempotent
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api status twice",
          "clientNameLangMap": { "eng": "api status twice" },
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
    And the JSON value at "response.status" should be "INACTIVE"
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "INACTIVE" }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "INACTIVE"

    # and back, in one step, from a client that has been deactivated twice
    When I send a "PATCH" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "status": "ACTIVE" }
      }
      """
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"

  # UpdateClientRequest.Status has no omitempty and normalizeStatus("") fails, so
  # a PUT is not a partial update with a remembered status: leaving it out is an
  # invalid request, not an instruction to keep the stored value.
  Scenario: Update rejects a payload that omits status
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api status put",
          "clientNameLangMap": { "eng": "api status put" },
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
    When I send a "PUT" request to "/client-mgmt/client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api status put renamed",
          "clientNameLangMap": { "eng": "api status put renamed" },
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
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

    # the client is untouched by the rejected update
    When I send a "GET" request to "/client-mgmt/client/{{cid}}"
    Then the response status should be 200
    And the JSON value at "response.status" should be "ACTIVE"
