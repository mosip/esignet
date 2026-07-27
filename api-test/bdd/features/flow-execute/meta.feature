@flow-execute
Feature: flow metadata endpoint (positive read)
  A read of /flow/meta — the flow module's public metadata. Not a login flow, so
  it needs no session; it confirms the endpoint is reachable and well-formed.

  Scenario: flow metadata is served and reports the registration-flow toggle
    When I send a "GET" request to "/flow/meta"
    Then the response status should be 200
    And the JSON path "isRegistrationFlowEnabled" should exist
    And the JSON path "i18n.languages" should exist
