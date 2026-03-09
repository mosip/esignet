#@smokeAndRegression
Feature: Esignet Login Options Page
  This feature file is for verifying the Login options page

  @smoke @AuthenticaionPage
  Scenario Outline: Verifying Login page Options
   Given user captures the authorize url
   Then verify dropdown language selection is present
   And verify multiple options for login is available
   And authentication screen should show login options based on acr_values from url
   And verify more ways to signIn option is available
   When user selects "<other lang>" from the language dropdown
   Then verify the UI is displayed in selected language "<text>"
   When Click on Language selection option
   And Select the mandatory language
   
  Examples:
   | other lang | text   |
   | हिंदी        | लॉगिन    |
  