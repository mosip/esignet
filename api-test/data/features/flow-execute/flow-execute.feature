@flow-execute
Feature: POST /flow/execute — entry-point validation
  Entry-point rejection cases the OpenID conformance suite cannot reach — it only
  drives the happy path to obtain a code. The accepted path (a real execution that
  proceeds to login) is covered by the e2e surface. All are pure API calls.
  Entry-point validation errors use the flat {code, message:{key}} error shape
  with HTTP 400 (distinct from the client-mgmt MOSIP-200-with-errors shape).

  Scenario: An unknown execution ID is rejected
    When I send a "POST" request to "/flow/execute" with body:
      """
      { "executionId": "bogus-does-not-exist" }
      """
    Then the response status should be 400
    And the JSON value at "code" should be "FES-1004"

  Scenario: A request with no flow type is rejected
    When I send a "POST" request to "/flow/execute" with body:
      """
      {}
      """
    Then the response status should be 400
    And the JSON value at "code" should be "FES-1005"

  Scenario: An unknown flow type is rejected
    When I send a "POST" request to "/flow/execute" with body:
      """
      { "flowType": "totally-unknown" }
      """
    Then the response status should be 400
    And the JSON value at "code" should be "FES-1005"
