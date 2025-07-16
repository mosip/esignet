# @smokeAndRegression
Feature: Esignet signUp Options Page
  This feature file is for verifying the signUp options page

@smoke @SignUpAndMobileNumberRegistration
Scenario Outline: Signup and Mobile Number Registration
  Given click on Sign In with eSignet
  Then validate that the logo is displayed
  Then verify Sign-Up with Unified Login option should be displayed

  Given user clicks on the Sign-Up with Unified Login hyperlink
  Then verify user is navigated to the Mobile Number Registration screen

  Then user verify header text
  And user verify a text box for entering an 8–9 digit mobile number
  And user verify a Continue button
  And user verify an option to navigate back to the previous screen
  And user verify an option to select preferred language
  And user verify footer text and logo
  Then user verify the mobile number text field should be pre-filled with country code
  Then user verify the help text in mobile number text field is displayed
 
  When user enters "<less than 8 digit>" in the mobile number text box
  And user tabs out
  Then verify the error message Enter valid username is displayed
  And validate that the Continue button remain disabled

  When user enters "<8 digit number>" in the mobile number text box
  Then the placeholder will be replaced with the entered mobile number
  And user tabs out
  Then validate that the Continue button enabled
  And verify no error message is displayed
  
  When user enters "<9 digit number>" in the mobile number text box
  And user tabs out
  Then validate that the Continue button enabled
  And verify no error message is displayed

  When user enters "<8 digit starting with 0>" in the mobile number text box
  And user tabs out
  Then verify the error Number cannot start with zero.Enter valid username is shown

  When user enters "<8 zeros>" in the mobile number text box
  And user tabs out
  Then verify the error Number cannot start with zero.Enter valid username is shown

  When user enters "<9 digit starting with 0>" in the mobile number text box
  And user tabs out
  Then verify the error Number cannot start with zero.Enter valid username is shown

  When user enters "<9 zeros>" in the mobile number text box
  And user tabs out
  Then verify the error Number cannot start with zero.Enter valid username is shown

  When user enters "<moreThanNineDigit>" in the mobile number text box
  Then verify the mobile number field should contain only 9 digits

  When user enters "<specialChars>" in the mobile number text box
  Then verify the mobile number field should remain empty or accept only number

  When user enters "<alphaNumeric>" in the mobile number text box
  Then verify the mobile number field should contain only numeric characters

  When user clicks on the navigate back button
  Then verify user is redirected to the previous screen
  
  Given user clicks on the Sign-Up with Unified Login hyperlink
  When user clicks the browser back button
  Then verify user is redirected to the previous screen

Examples:
  | less than 8 digit | 8 digit number | 9 digit number | 8 digit starting with 0 | 8 zeros  | 9 digit starting with 0 | 9 zeros   | moreThanNineDigit | specialChars | alphaNumeric |
  | 12345             | 99008743       | 987654321      | 012345678               | 00000000 | 0123456789              | 000000000 | 98765432112       | @#$%^&*      | abc123       |
  
  
@smoke @OtpPage
Scenario Outline: OTP input acceptance and Verify button state
  Given click on Sign In with eSignet
  Given user clicks on the Sign-Up with Unified Login hyperlink
  When user enters "<already registered number>" in the mobile number text box
  And user clicks on the Continue button
  Then verify user is navigated to the OTP screen

  Given user is on the OTP screen
  Then user verifies the OTP screen header is displayed as Enter OTP
  And user verifies the OTP screen description should contain a masked mobile number
  And user verifies the OTP input field is visible
  And user verifies the Verify OTP button is visible
  And user verifies a 3-minute countdown timer is displayed
  And user verifies the Resend OTP option is visible
  And user verifies an option to go back and update the mobile number is be present

  When user clicks the back button on the OTP screen
  Then verify user is redirected back to the Registration screen

  When user enters "<already registered number>" in the mobile number text box
  And user clicks on the Continue button
  Then verify user is navigated to the OTP screen

  And user waits for OTP timer to expire
  When user enters "<expired_otp>" as a Otp 
  And user clicks on the Verify OTP button
  Then verify an error message OTP expired. Please request a new one and try again. is displayed at the top

  Then user clicks on the Resend OTP button
  When user enters "<invalid_otp>" as a Otp
  And user clicks on the Verify OTP button
  Then verify an error message OTP authentication failed. Please try again. is displayed at the top
 
  When user enters "<special_characters>" as a Otp
  Then verify OTP field is rejecting special characters

  When user enters "<alphabets>" as a Otp
  Then verify OTP field is rejecting alphabets

  When user enters "<alphanumeric_characters>" as a Otp
  Then verify OTP field is rejecting alphanumeric characters
  
  When user enters "<incomplete_otp>" as a Otp
  Then validate the "Verify" button is disabled

  When user enters the complete 6-digit OTP
  Then verify OTP is masked as soon as it is entered
  And validate the "Verify" button is enabled
  And user clicks on the Verify OTP button
  Then verify Sign-Up Failed! is displayed as a heading
  And verify the failure message The provided mobile number is already registered. Please use the Login option to proceed. shown
  And verify a Login button is visible

  Then user clicks on the Login button
  Given user clicks on the Sign-Up with Unified Login hyperlink
  When user enters "<valid mobile number>" in the mobile number text box
  And user clicks on the Continue button
  Then verify user is navigated to the OTP screen
  When user enters the complete 6-digit OTP
  And user clicks on the Verify OTP button
  And verify user is redirected to the success screen
  And verify the header Successful! is displayed
  And verify the message Your mobile number has been verified successfully. Please continue to setup your account and complete the registration process. is displayed
  And verify a Continue button is displayed
  
Examples:
  | already registered number | expired_otp | invalid_otp | special_characters | alphabets | alphanumeric_characters | incomplete_otp | valid mobile number |
  | 991678222                 | 111111      | 000000      | @#%&*!             | ABCDEF    | ABC123                  | 12             | 782910667           |
  
