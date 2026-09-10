Feature: Browser Compatibility check Screen
  This feature file is for verifying the browser compatibility check screen shown
  when the user's browser is not supported.

  @smoke @BrowserCompatibility
  Scenario: TC_Browser_Compatibility_check_01 - Verify the text labels, elements in browser compatibility screen
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button

   Then verify consent should ask user to proceed in attention page
   When user's browser is overridden to an unsupported user agent
   And clicks on proceed button in attention page
   And clicks on proceed button in next page

   Then verify the browser compatibility screen header is displayed as "Alert!"
   And verify the browser compatibility screen subheader is displayed
   And verify the browser compatibility screen subheader message is "Oops! It looks like you're using an unsupported browser.  Please consider upgrading to the latest version of the browser for the best experience. Thank you for your understanding"
   And verify the Okay button is displayed on the browser compatibility screen
   And verify the logo is displayed on the browser compatibility screen
   And verify the language dropdown is displayed on the browser compatibility screen
   And verify the powered by eSignet footer is displayed on the browser compatibility screen

  @smoke @BrowserCompatibility
  Scenario: Verify the Okay button is clickable on the browser compatibility screen
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button

   Then verify consent should ask user to proceed in attention page
   When user's browser is overridden to an unsupported user agent
   And clicks on proceed button in attention page
   And clicks on proceed button in next page

   Then verify the Okay button is clickable on the browser compatibility screen
   When user clicks on the Okay button on the browser compatibility screen
   Then verify user is redirected to the relying party with the incompatible browser error
