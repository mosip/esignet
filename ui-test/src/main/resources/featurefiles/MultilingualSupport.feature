@smokeAndRegression
Feature: Multilingual Support
  This feature file contains test cases for multilingual support functionality

@smoke @multilingualSupport
Scenario: Verify whether launching url navigates to health service UI Screen
  When Launch the health services url
  Then Verify whether launching url navigates to health service UI Screen
  And User should be redirected to Health service portal UI screen

@smoke @multilingualSupport
Scenario: User click on sign in with esignet option displayed in healthService.com screen
  When Launch the health services url
  Then User should be redirected to Health service portal UI screen
  When User clicks on sign in with esignet option
  Then Verify user is redirected to IDP UI screen
  And User should be redirected to IDP UI screen
