# @smokeAndRegression
Feature: Esignet signUp Options Page
  This feature file is for verifying the signUp options page

@smoke @SignUpAndMobileNumberRegistration
Scenario Outline: Signup and Mobile Number Registration
  Given click on Sign In with eSignet
  Then validate that the logo is displayed
  Then verify Sign-Up with Unified Login option should be displayed

  When user clicks on the Sign-Up with Unified Login hyperlink
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
  
  When user clicks on the Sign-Up with Unified Login hyperlink
  When user clicks the browser back button
  Then verify user is redirected to the previous screen

Examples:
  | less than 8 digit | 8 digit number | 9 digit number | 8 digit starting with 0 | 8 zeros  | 9 digit starting with 0 | 9 zeros   | moreThanNineDigit | specialChars | alphaNumeric |
  | 12345             | 99008743       | 987654321      | 012345678               | 00000000 | 0123456789              | 000000000 | 98765432112       | @#$%^&*      | abc123       |
  
  
@smoke @OtpPage
Scenario Outline: OTP input acceptance and Verify button state
  Given click on Sign In with eSignet
  When user clicks on the Sign-Up with Unified Login hyperlink
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
  When user clicks on the close icon of the error message
  Then verify the error message is not visible
  
  Then user clicks on the Resend OTP button
  When user enters "<invalid_otp>" as a Otp
  And user clicks on the Verify OTP button
  Then verify an error message OTP authentication failed. Please try again. is displayed at the top
  And verify error message disappears after 10 seconds
  
  When user enters "<invalid_otp>" as a Otp
  And user clicks on the Verify OTP button
  Then verify an error message OTP authentication failed. Please try again. is displayed at the top
  
  When user enters "<special_characters>" as a Otp
  Then verify error message disappears as user starts typing in the input field
  And verify OTP field is rejecting special characters

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
  When user clicks on the Sign-Up with Unified Login hyperlink
  And user enters "<valid mobile number>" in the mobile number text box
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
  

@smoke @accountSetupValidation
Scenario Outline: Completing Registration Process
  Given click on Sign In with eSignet
  When user clicks on the Sign-Up with Unified Login hyperlink
  And user enters "<valid_mobile_number>" in the mobile number text box
  And user clicks on the Continue button
  When user enters the complete 6-digit OTP
  And user clicks on the Verify OTP button
  And verify user is redirected to the success screen
  When user click on Continue button in Success Screen
  Then verify setup account screen is displayed with header Setup Account
  And verify description Please enter the requested details to complete your registration.
  And verify a Username field should be visible
  And verify an option to enter Full Name in Khmer
  And verify an option to setup Password
  And verify an option to Confirm Password
  And verify an option to mask or unmask the entered password
  And verify an option to view password policy by clicking on the "i" icon
  And verify an option to check the checkbox to agree to T&C and Privacy Policy
  And verify it should display Continue button

  Then verify the Username field is auto-filled with the verified mobile number
  And validate the Username field should be non-editable

  And verify the watermark text in the Full Name in Khmer field it should be as "Enter Full Name in Khmer"

  Then user clicks on Language Selection Option
  And user selects Khmer from the language dropdown
  And verify page rendered in selected language
  When user enters text "<in other language>" in the Full Name in Khmer field
  And user tabs out from the field
  Then verify an error message Should be able to enter only Khmer characters is displayed below the field

  Then user clicks on Language Selection Option
  And user selects English from the language dropdown
  When user enters text "<more than 30 characters>" in the Full Name in Khmer field
  Then verify the field restrict the input to 30 characters only

  When user enters text "<only spaces>" in the Full Name in Khmer field
  And user tabs out from the field
  Then verify an error message Please enter a valid name. is displayed below the field

  And verify the watermark text in the Password field is "Enter Password"

  When user enters "<invalid password>" in the Password field 
  And user tabs out from the field
  Then verify an error message Password does not meet the password policy. displayed below the Password field
  
  When user enters "<less than 8 characters>" in the Password field
  And user tabs out from the field
  Then verify an error message Password does not meet the password policy. displayed below the Password field
  
  When user enters "<more than 20 characters>" in the Password field
  Then validate the field restrict the input to 20 characters only

  And verify the watermark text in the Confirm Password field is "Enter Password"

  When user enters "<valid password>" in the Password field
  Then user enters "<different password>" in the Confirm Password field
  And user tabs out from the field
  Then verify an inline error message Password and Confirm Password do not match. displayed below Confirm Password field

  Then user enters "<more than 20 character>" in the Confirm Password field
  And verify the field should restrict the password to 20 characters only
  
  Then user enters "<less than 8 character>" in the Confirm Password field
  And user tabs out from the field
  Then verify an inline error message Password and Confirm Password do not match. displayed below Confirm Password field

  Then validate the Password field is masked
  And validate the Confirm Password field is masked

  When user clicks on the unmask icon in the Password field
  Then validate the Password field is unmasked

  When user clicks on the unmask icon in the Confirm Password field
  Then validate the Confirm Password field is unmasked

  When user clicks again on the unmask icon in the Password field
  Then validate the Password field is masked

  When user clicks again on the unmask icon in the Confirm Password field
  And validate the Confirm Password field is masked

  When user clicks on the "i" icon in the Password field
  Then verify the tooltip message Use 8 or more characters with a mix of alphabets and at least one number. is displayed

  When user clicks on the "i" icon in the Full Name in Khmer field
  Then verify the tooltip message Maximum 30 characters allowed with no alphabets or special characters, except space. is displayed

  When user does not check the terms and conditions checkbox
  Then verify the Continue button will be in disabled state

  Then verify the terms and conditions message

  When user enters text "<special_characters>" in the Full Name in Khmer field
  And user tabs out from the field
  Then verify it restricts such input with an error message Full Name has to be in Khmer only.

  When user enters text "<alphanumeric_input>" in the Full Name in Khmer field
  And user tabs out from the field
  Then verify it restricts such input with an error message Full Name has to be in Khmer only.

  When user enters text "<numeric_input>" in the Full Name in Khmer field
  And user tabs out from the field
  Then verify it restricts such input with an error message Full Name has to be in Khmer only.

  When user clicks on the Terms & Conditions hyperlink
  Then verify a pop-up window for Terms and Conditions is displayed

  When user closes the Terms and Conditions popup
  Then verify user is navigated back to the Account Setup screen

  When user clicks on the Privacy policy hyperlink
  Then verify a pop-up window for Privacy Policy is displayed

  When user closes the privacy policy popup
  Then verify user is navigated back to the Account Setup screen

  Then verify the Continue button is disabled when mandatory fields are not filled in Account Setup screen
  
  When user enters text "<valid name>" in the Full Name in Khmer field
  And user enters "<valid password>" in the Password field
  Then verify the Continue button is disabled when only two mandatory fields are filled 
  
Examples:
  | valid_mobile_number | in other language | more than 30 characters       | only spaces | invalid password | less than 8 characters | more than 20 characters          | valid password | different password | more than 20 character         | less than 8 character | special_characters | alphanumeric_input | numeric_input | valid name | valid confirm password |
  | 782910669           | John Doe          | ប្រសិនបើប្រយោគនេះមានរ៉ាំរ៉ាវហួសពី៣០តួអក្សរ |              | ABCD@#$%         | aBc@1                  | Passwordmorethantwenty@char      | Password@1     | password1          | ConfirmPasswordmorethantwenty  | pass@1                | !@#$%^&            | Abc1234            | 1234567       | សុខសេរី      | Password@1             |
 