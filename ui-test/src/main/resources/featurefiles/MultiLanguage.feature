@smokeAndRegression
Feature: Multi language scenario's in eSignet
  This feature file is for verifying the multi language feature

  @smoke
  Scenario: Verify the language in browser cookie
    Given user captures the authorize url
    Then verify IDP UI uses default language configured in env-config
    When click on Language selection option
    And select the mandatory language
    And get the cookies
    Then validate the language in cookie
