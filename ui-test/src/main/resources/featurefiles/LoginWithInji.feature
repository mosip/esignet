@smokeAndRegression
Feature: Login with INJI in eSignet
  This feature file is for verifying the login with Inji feature

  @smoke
  Scenario: Verify the Login with INJI page
    Given user captures the authorize url
    When Click on Login with Inji
    Then validate the logo alignment