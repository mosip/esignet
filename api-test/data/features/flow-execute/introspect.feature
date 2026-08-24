@flow-execute
Feature: POST /oauth2/introspect — request and client-authentication validation
  The shape of the RFC 7662 introspection endpoint, checked without a token and
  without a registered client. eSignet authenticates the caller with
  private_key_jwt and does so before it looks at the submitted token, so every
  rejection reachable from here is a client-authentication or malformed-request
  one: 400 invalid_request when the request does not even name a client, 401
  invalid_client when it names one it cannot prove it is.

  What needs a real token instead — an issued token reported active with its
  client, subject, scope and DPoP binding, an unissued one reported inactive
  rather than as an error, and a missing token parameter behind a client
  assertion that does verify — is covered on the e2e surface, which registers a
  client and drives a login to obtain one.

  # Bodies are form-encoded, so each scenario sets Content-Type explicitly:
  # the generic step defaults to application/json.

  Scenario: A request naming no client is rejected as malformed
    Given I set header "Content-Type" to "application/x-www-form-urlencoded"
    When I send a "POST" request to "/oauth2/introspect" with body:
      """
      token=some-opaque-token
      """
    Then the response status should be 400
    And the JSON value at "error" should be "invalid_request"
    And the JSON path "error_description" should exist

  Scenario: An empty request is rejected as malformed
    Given I set header "Content-Type" to "application/x-www-form-urlencoded"
    When I send a "POST" request to "/oauth2/introspect"
    Then the response status should be 400
    And the JSON value at "error" should be "invalid_request"

  # A client id alone is not client authentication: without an assertion there is
  # nothing to verify against the registered key, so the call must not be trusted.
  Scenario: A client id with no assertion is refused
    Given a new client id as "unproven_client_id"
    And I set header "Content-Type" to "application/x-www-form-urlencoded"
    When I send a "POST" request to "/oauth2/introspect" with body:
      """
      token=some-opaque-token&client_id={{unproven_client_id}}
      """
    Then the response status should be 401
    And the JSON value at "error" should be "invalid_client"
    And the JSON path "error_description" should exist

  Scenario: An unverifiable client assertion is refused
    Given a new client id as "unproven_client_id"
    And I set header "Content-Type" to "application/x-www-form-urlencoded"
    When I send a "POST" request to "/oauth2/introspect" with body:
      """
      token=some-opaque-token&client_id={{unproven_client_id}}&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=not.a.jwt
      """
    Then the response status should be 401
    And the JSON value at "error" should be "invalid_client"

  # RFC 7662 s2.1 defines introspection as a POST; a GET would put the token in
  # the request URI, where it lands in access logs and referrers.
  Scenario: The endpoint is not exposed over GET
    When I send a "GET" request to "/oauth2/introspect"
    Then the response status should be 405
