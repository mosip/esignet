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
   When click on Language selection option
   And select the mandatory language
   
  Examples:
   | other lang | text   |
   | हिंदी        | लॉगिन    |
   
  @mobile @mobileViewFeatures
  Scenario: Verifying the UI in mobile view
   When user triggers the authorization endpoint, the response should have status code 200 and contain valid HTML with JS content
   Given user captures the authorize url
   And click on Language selection option
   And select the mandatory language
   Then user views the portal on multiple screen sizes
   And user verifies the behavior after resizing the browser window to different dimensions
   And user verify the otp button remain visible and aligned after resizing
   Then verify dropdown language selection is present
   And verify multiple options for login is available
   And verify more ways to signIn option is available
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   
   
   @smoke @supportOfPrefixAndPostfix
  Scenario: Verifying support for multiple prefix and postfix type for the individual ID
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then verify mobile number option is present for authentication
   Then verify nrc id option is present for authentication
   Then verify vid option is present for authentication
   Then verify email option is present for authentication
   Then verify mobile number selected for authentication
   And clicks on prefix number button in authentication screen page
   Then verify khm country prefix displayed for mobile number
   Then verify ind country prefix displayed for mobile number
   And clicks on prefix number button in authentication screen page
   Then verify get otp button is disabled in authentication screen
   Then user enters Registered mobile number into the mobile number field
   Then verify get otp button is enabled in authentication screen
   And user click on get otp button
   Then verify user navigate to verify otp screen
   Then verify the otp verification button is disabled on the verification screen
   When user enters the correct otp
   Then verify the otp verification button is enabled on the verification screen
   And click on verify Otp button
   Then verify user navigate to Attention screen
   Then clicks on cancel button in attention consent screen page
   Then clicks on discontinue button in attention screen page
   And clicks on sign in with esignet button in login page
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then clicks on vid option button in authentication screen page
   When user enters invalid vid into vid field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into vid field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into vid field
   Then verify get otp button is disabled in authentication screen
   Then clicks on email option button in authentication screen page
   When user enters invalid email into email field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into email field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into email field
   Then verify get otp button is disabled in authentication screen
   