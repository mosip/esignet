#@smokeAndRegression
Feature: Esignet Login Options Page
  This feature file is for verifying the Login options page

  @smoke @loginOptionsPageVerification
  Scenario: Verify the Login options page
    Given click on Sign In with eSignet
    Then validate that the logo is displayed
    #And validate the logo alignment
    #And validate that header is displayed
    #And validate that sub-header is displayed
    #And I validate the outcomes
    #And check more outcomes
    
@smoke @loginfeature
Scenario Outline: Verify the Login feature
  Given click on Sign In with eSignet
  When user clicks on the Sign-Up with Unified Login hyperlink
  Then verify user is navigated to the Mobile Number Registration screen
  Then user verify the mobile number text field should be pre-filled with country code
  Then user verify a text box for entering an 8–9 digit mobile number
  Then user verify a Continue button
  
  When user enters "<valid mobile number>" in the mobile number text box
  Then validate that the Continue button enabled
  And user clicks on the Continue button
  Then verify user is navigated to the OTP screen
  When user enters the complete 6-digit OTP
  And user clicks on the Verify OTP button
  
  And verify user is redirected to the success screen
  Then user clicks on continue button on success page
  And user prolonged to registration page
  When user enters text "<khmer name>" in the Full Name in Khmer field
  When user enters "<shortPassword>" into the password field
  And user click out side on password field
  Then verify invalid password error should be shown
  When user enters "<longPassword>" into the password field
  Then system should restrict password input to max allowed
  When user enters "<specialCharacter>" into the password field
  When user enters text "<valid name>" in the Full Name in Khmer field
  When user enters "<valid password>" into the password field
  When user enters "<confrimPassword>" into password field for confirm
  And user clicks on agrees terms condition check-box
  Then user clicks on continue button on registration page
  Then it will redirect to congratulations on login page
  And user clicks on login button
  And verify user is redirected to login page of relying portal
  And user clicks on login with password button
  Then verify login button is disabled

  When user enter "<less than 8 digit>" into mobile number field
  Then verify login button is disabled
  And user enters "<registered password>" into password field
  And user clicks on login button in login page
  Then verify error Please Enter Valid Individual ID. is displayed

  When user clicks on the navigate back button
  And user clicks on login with password button
  When user enters "<registered password>" into password field
  Then verify login button is disabled
  And user enter "<9 zero>" into mobile number field
  And user clicks on login button in login page
  Then verify error Please Enter Valid Individual ID. is displayed

  And user enter "<registered mobile number>" into mobile number field
  And user enters "<specialCharacter>" into password field
  And user tabout of password field
  Then verify error Please Enter Valid Password is displayed 

  When user enter "<registered mobile number>" into mobile number field
  And user enters "<Unregistered password>" into password field
  And user clicks on login button in login page
  Then verify error Username or password is not valid. Please enter valid credentials. is displayed
  When user click on the close icon in error message
  Then verify the error message disappears

  When user enter "<Unregistered mobile number>" into mobile number field
  And user enters "<registered password>" into password field
  And user clicks on login button in login page
  Then verify error Username or password is not valid. Please enter valid credentials. is displayed
  Then verify error message disappears automatically after 10 seconds

  When user enter "<registered mobile number>" into mobile number field
  And user enters "<random char>" into password field
  And user clicks on login button in login page
  Then verify error Please Enter Valid Password is displayed

  And user enter "<registered mobile number>" into mobile number field
  And user enters "<registered password>" into password field
  And user tabout of password field
  Then verify login button is enabled
  And user clicks on login button in login page
  Then verify consent should ask user to proceed in attention page
  And clicks on proceed button in attention page
  And clicks on proceed button in next page
  Then select the e-kyc verification provider
  And clicks on proceed button in e-kyc verification provider page
  And user select the check box in terms and condition page
  And user clicks on proceed button in terms and condition page 
  And user clicks on proceed button in camera preview page
  Then user is navigated to consent screen once liveness check completes
  And verify the header Please take appropriate action in xx:xx is displayed
  And verify logos of the relying party and e-Signet is displayed
  And verify the essential claims with "i" icon is displayed
  And verify required essential claims is displayed
  And verify the voluntary claims with "i" icon is displayed
  And verify list of voluntary claims displayed
  And verify allow button is visible consent screen
  And verify cancel button is visible consent screen
  Then verify the tooltip message for Essential Claims info icon
  And verify Phone Number should be listed under Essential Claims
  And Verify the state of phone number field under Essential Claims
  When user click on cancel button in consent screen
  Then verify the header Please Confirm is displayed
  And verify the message Are you sure you want to discontinue the login process? is displayed
  And verify discontinue button is displayed
  And verify stay button is displayed
  When user click on Stay button
  Then verify user is retained on same consent screen
  When user click on cancel button in consent screen
  Then user click on Discontinue button
  And verify user is redirected to relying party portal page
  
  Given click on Sign In with eSignet
  And user clicks on login with password button
  And user enter "<registered mobile number>" into mobile number field
  And user enters "<registered password>" into password field
  And user clicks on login button in login page

  When user enables the master toggle of voluntary claims
  And click on allow button in consent page
  Then verify user is navigated to landing page of relying party
  And verify welcome message is displayed with the registered name

Examples:
  | valid mobile number | shortPassword | longPassword          |  khmer name    |valid name | valid password  | confrimPassword | registered mobile number | specialCharacter| less than 8 digit | registered password | 9 zero    | Unregistered password | Unregistered mobile number |  random char |
  | 74638418            |  pa@12        | prodevtester@12345576 |   បាសាន់        |      អ៊ូសាស៊ី | Pass@2004       |   Pass@2004      | 74638418                | #$%@#!          | 96785             | Pass@2004           | 000000000 | pass@1234             | 87546789                   | q1@13m       |