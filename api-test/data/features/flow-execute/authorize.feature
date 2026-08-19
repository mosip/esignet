@flow-authz-neg
Feature: GET /oauth2/authorize — request validation
  Rejection cases the conformance suite cannot reach: they assert eSignet rejects a
  malformed authorize request before any login begins. The accepted-request path is
  covered by the e2e surface, which drives authorize through to a code. Driven with a real,
  pre-registered client id (FLOW_CLIENT_ID) so redirect/param validation runs
  against a genuine client — no partner/policy or OTP needed. Requests are made
  without following redirects so the 302 and its Location (errorCode) are asserted
  directly (following it would land on the HTML /error page).

  # eSignet answers an invalid authorize with 302 -> /error?errorCode=...&errorMessage=...
  # rather than a JSON body. gated by @flow-authz-neg + FLOW_CLIENT_ID.

  Background:
    Given a registered client id is configured

  Scenario: An unregistered redirect_uri is rejected
    When I send a "GET" request to "{{authz_endpoint}}?response_type=code&client_id={{client_id}}&scope=openid&redirect_uri=https://evil.example.org/cb&state=s1&nonce=n1&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256" without following redirects
    Then the response status should be 302
    And the redirect location should contain "errorCode=invalid_request"

  # A freshly minted id rather than a literal: nothing registers it, so it stays
  # unknown even on a deployment that has been up for a long time.
  Scenario: An unknown client id is rejected
    Given a new client id as "unknown_client_id"
    When I send a "GET" request to "{{authz_endpoint}}?response_type=code&client_id={{unknown_client_id}}&scope=openid&redirect_uri=https://evil.example.org/cb&state=s1&nonce=n1&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256" without following redirects
    Then the response status should be 302
    And the redirect location should contain "/error"
