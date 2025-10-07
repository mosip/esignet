@smokeAndRegression
Feature: Multi language scenario's in eSignet
  This feature file is for verifying the multi language feature

  @smoke
  Scenario: Verify the language in browser cookie
    Given Click on Sign In with eSignet
    When Click on Language selection option
    And Select the mandatory language
    And Get the cookies
    Then Validate the language in cookie
