@client-mgmt
Feature: /client-mgmt/oidc-client and /client-mgmt/oauth-client — the other two client profiles
  eSignet registers clients under three profiles, each on its own route, and the
  rest of this directory only ever drives one of them. create-client.feature,
  update-client.feature and patch-client.feature all post to /client-mgmt/client
  — the "client" profile — so the oidc-client and oauth-client routes, and the
  profile-specific rules behind them, went entirely unexercised.

  The three differ only in what they accept, and the difference is not cosmetic:

    route                       clientNameLangMap    additionalConfig
    /client-mgmt/oidc-client    must be ABSENT       must be ABSENT
    /client-mgmt/oauth-client   REQUIRED             must be ABSENT
    /client-mgmt/client         REQUIRED             allowed

  So a body that is valid on one route is a rejection on another, which is what
  the cross-profile scenarios below assert. Both validators apply the same rules
  on create and on update, so each rule is checked on POST and on PUT.

  Also here: the non-RSA branches of public-key validation. Every other feature
  file registers an RSA key, leaving the EC arm — the curve allow-list and the
  coordinate checks — untested. Those cases are all rejections; see the comment
  above them for why there is no accepted-EC-key scenario.

  # MOSIP API convention, as everywhere in this directory: both outcomes are
  # HTTP 200. A rejection populates "errors" and nulls "response".

  Background:
    Given I authenticate as admin
    And a fresh request timestamp
    And a generated RSA public key as "pubjwk"
    And a new client id as "cid"

  # ------------------------------------------------------- oidc-client --

  # The oidc profile stores clientName bare rather than as a language map, which
  # is why supplying one is an error rather than being ignored.
  Scenario: Register and update a client under the oidc profile
    When I send a "POST" request to "/client-mgmt/oidc-client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api oidc profile",
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
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

    When I send a "PUT" request to "/client-mgmt/oidc-client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api oidc profile updated",
          "status": "ACTIVE",
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
    And the JSON value at "response.clientId" should be "{{cid}}"

  # The same body the "client" profile requires is a rejection here — this is
  # the clearest statement that the routes are not interchangeable.
  Scenario: The oidc profile rejects a clientNameLangMap on create and on update
    When I send a "POST" request to "/client-mgmt/oidc-client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api oidc langmap",
          "clientNameLangMap": { "eng": "api oidc langmap" },
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
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

    # rejected on the update path too — the client id need not exist, the
    # profile rule is checked before the row is looked up
    When I send a "PUT" request to "/client-mgmt/oidc-client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api oidc langmap",
          "clientNameLangMap": { "eng": "api oidc langmap" },
          "status": "ACTIVE",
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

  # additionalConfig carries the protocol switches (PKCE, PAR, DPoP) that
  # additional-config.feature exercises on the "client" profile. The oidc
  # profile does not accept them at all.
  Scenario: The oidc profile rejects an additionalConfig block
    When I send a "POST" request to "/client-mgmt/oidc-client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api oidc addlcfg",
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
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  # ------------------------------------------------------ oauth-client --

  # oauth sits between the other two: it wants the language map that oidc
  # forbids, but refuses the additionalConfig that "client" allows.
  Scenario: Register and update a client under the oauth profile
    When I send a "POST" request to "/client-mgmt/oauth-client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api oauth profile",
          "clientNameLangMap": { "eng": "api oauth profile" },
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
    And the JSON value at "response.clientId" should be "{{cid}}"
    And the JSON value at "response.status" should be "ACTIVE"

    When I send a "PUT" request to "/client-mgmt/oauth-client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api oauth profile updated",
          "clientNameLangMap": { "eng": "api oauth profile updated" },
          "status": "ACTIVE",
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
    And the JSON value at "response.clientId" should be "{{cid}}"

  # The mirror image of the oidc case above: here the language map is mandatory,
  # and the body oidc accepts is the one that fails.
  Scenario: The oauth profile requires a clientNameLangMap on create and on update
    When I send a "POST" request to "/client-mgmt/oauth-client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api oauth nolangmap",
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
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

    When I send a "PUT" request to "/client-mgmt/oauth-client/{{cid}}" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientName": "api oauth nolangmap",
          "status": "ACTIVE",
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

  Scenario: The oauth profile rejects an additionalConfig block
    When I send a "POST" request to "/client-mgmt/oauth-client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api oauth addlcfg",
          "clientNameLangMap": { "eng": "api oauth addlcfg" },
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
    And the JSON value at "errors.0.errorCode" should be "invalid_input"

  # ------------------------------------------- non-RSA public keys --

  # These are all rejections, and deliberately so. A public key is unique across
  # clients — registering one twice is refused as invalid_public_key — and the
  # harness can only generate RSA keys, so a committed EC key would be accepted
  # on a suite's first run against a deployment and rejected as a duplicate on
  # every run after. Rejections carry no such cost: validation refuses them
  # before anything is stored, so they are repeatable forever.
  #
  # Adding an accepted-EC-key scenario means teaching the harness to generate EC
  # keys (a new step alongside "a generated RSA public key as"), not committing
  # a fixture.

  Scenario: A key on an unsupported curve is rejected
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api ec curve",
          "clientNameLangMap": { "eng": "api ec curve" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {
            "kty": "EC",
            "crv": "P-999",
            "x": "YWuM_9MpH_NOf4OYVaJp3jZr6mUVjPr3XQtR6VZITPY",
            "y": "q_8GWBcAsLbNIsfnGk61sPNx411JFJ_PZqGlkH6zxu4"
          },
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_public_key"

  # An EC key is defined by both coordinates; one alone is not a point.
  Scenario: An EC key missing a coordinate is rejected
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api ec coord",
          "clientNameLangMap": { "eng": "api ec coord" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {
            "kty": "EC",
            "crv": "P-256",
            "x": "YWuM_9MpH_NOf4OYVaJp3jZr6mUVjPr3XQtR6VZITPY"
          },
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_public_key"

  # Present but undecodable: the coordinates are checked as base64url, not just
  # for being non-empty.
  Scenario: An EC key with an undecodable coordinate is rejected
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api ec b64",
          "clientNameLangMap": { "eng": "api ec b64" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {
            "kty": "EC",
            "crv": "P-256",
            "x": "not!valid!base64",
            "y": "q_8GWBcAsLbNIsfnGk61sPNx411JFJ_PZqGlkH6zxu4"
          },
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_public_key"

  # Only RSA and EC are understood; anything else falls to the default arm
  # rather than being passed through to the keystore.
  Scenario: A key of an unsupported type is rejected
    When I send a "POST" request to "/client-mgmt/client" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "clientId": "{{cid}}",
          "clientName": "api okp kty",
          "clientNameLangMap": { "eng": "api okp kty" },
          "relyingPartyId": "api-e2e-rp",
          "logoUri": "https://example.org/logo.png",
          "redirectUris": ["https://example.org/cb"],
          "publicKey": {
            "kty": "OKP",
            "crv": "Ed25519",
            "x": "YWuM_9MpH_NOf4OYVaJp3jZr6mUVjPr3XQtR6VZITPY"
          },
          "userClaims": ["name"],
          "authContextRefs": ["mosip:idp:acr:static-code"],
          "grantTypes": ["authorization_code"],
          "clientAuthMethods": ["private_key_jwt"]
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_public_key"
