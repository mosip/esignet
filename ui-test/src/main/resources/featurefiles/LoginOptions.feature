#@smokeAndRegression
Feature: Esignet Login Options Page
  This feature file is for verifying the Login options page

  @smoke @AuthenticaionPage
  Scenario Outline: Verifying Login page Options
   Given user captures the authorize url
   Then verify dropdown language selection is present
   And verify multiple options for login is available
   And verify more ways to signIn option is available
   When user selects "हिंदी" from the language dropdown
   Then verify the UI is displayed in "Hindi" language
   When Click on Language selection option
   And Select the mandatory language
   And authentication screen should show login options based on acr_values from url
  