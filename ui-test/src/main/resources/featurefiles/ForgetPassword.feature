#@smokeAndRegression
Feature: Esignet Login Options Page
  This feature file is for verifying the forget password options page

  @smoke @forgetPasswordOptionsVerification
  Scenario Outline: Verify the forget password options for phonenumber
    Given click on Sign In with eSignet
    Then validate that the logo is displayed
    Then user click on Login with password
    Then user verify forget password link
    Then user click on forget password link
    Then user verify browser redirected to reset-password
    Then user verify country code prefix
    Then user verify the water mark text inside phonenumber
    Then user verify country code is not editable
    Then user verify forget password heading
    Then user verify back button on forget password
    Then user verify subheading on forget password
    Then user verify username label on forget password
    Then user verify fullname label on forget password
    Then user verify continue button on forget password
    Then user verify footer on forget password

    When user enters "<number with starting 0>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "invalid"

    When user enters "<number with all zeros>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "invalid"

    When user enters "<8 digit number start with 0>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "invalid"

    When user enters "<alphanumeric input>" into the mobile number field
    Then mobile number input should remain empty

    When user enters "<special char input>" into the mobile number field
    Then mobile number input should remain empty

    When user enters "<9 digit number>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "valid"

    When user enters "<8 digit number>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "valid"

    Then user verify continue button is not enabled

    When user enters "<moreThanNineDigit>" into the mobile number field
    Then verify the mobile number field should restrict to 9 digits

    Examples:
      | 9 digit number | 8 digit number | number with starting 0 | number with all zeros | 8 digit number start with 0 | alphanumeric input | special char input | moreThanNineDigit |
      | 240578296      | 12345678       | 012345678              | 000000000             | 01234567                    | abcdef             | @#$%               | 98765432112       |

  @smoke @forgetPasswordFullNameVerification
  Scenario Outline: Verify the forget password options for fullName
    Given click on Sign In with eSignet
    Then user click on Login with password
    And user click on forget password link

    When user enters "<Numeric>" into the fullname field
    And user clicks outside the input to trigger validation
    Then user verify full name error message

    When user enters "<Alphanumeric>" into the fullname field
    And user clicks outside the input to trigger validation
    Then user verify full name error message

    When user enters "<English>" into the fullname field
    And user clicks outside the input to trigger validation
    Then user verify full name error message

    When user enters "<LongKhmer>" into the fullname field
    And user clicks outside the input to trigger validation
    Then user verify full name error message not displayed
    And only 30 characters are retained in the fullname field

    When user enters "<ValidKhmer>" into the fullname field
    And user clicks outside the input to trigger validation
    Then user verify full name error message not displayed

    Then user verify continue button is not enabled

    When user enters "<8 digit number>" into the mobile number field
    Then user verify continue button is enabled
    Then user click on continue button

    Then user waits for resend OTP button and verifies it's enabled or skipped
    And user waits and clicks on resend OTP, then validates 2 out of 3 attempts message
    And user waits for resend OTP button and verifies it's enabled or skipped
    Then user waits and clicks on resend OTP, then validates 1 out of 3 attempts message
    And user waits for resend OTP button and verifies it's enabled or skipped
    Then user waits and clicks on resend OTP, then validates 0 out of 3 attempts message
    And user waits for OTP timer to expire for fourth time
    Then validate the "Verify" button is disabled
    
    Examples:
      | Numeric   | Alphanumeric | English  | LongKhmer                                            | ValidKhmer | 8 digit number |
      | 240578296 | abc123       | myname   | សុខ សេរី សាន សុវណ្ណ សុភ័ក្រ សាន សុផល សុជាតា សុវណ្ណ                | សុខសេរី      | 12345678       |

  @smoke @forgetPasswordOTPVerification
  Scenario Outline: Verify the forget password options for otp
    Given click on Sign In with eSignet
    Then user click on Login with password
    And user click on forget password link

    When user enters "<ValidKhmer>" into the fullname field
    When user enters "<8 digit number>" into the mobile number field
    Then user click on continue button

    And user waits for OTP timer to expire
    When user enters "<expired_otp>" as a Otp
    And user clicks on the Verify OTP button
    Then verify an error message OTP expired. Please request a new one and try again. is displayed at the top
    When user clicks on the close icon of the error message
  	Then verify the error message is not visible

    Then user clicks on the Resend OTP button
    When user enters "<InvalidOrRandomOtp>" as a Otp
    And user clicks on the Verify OTP button
    Then verify an error message OTP authentication failed. Please try again. is displayed at the top
    And verify error message disappears after 10 seconds

    When user enters "<alphabets>" as a Otp
    Then verify OTP field is rejecting alphabets

    When user enters "<Alphanumeric>" as a Otp
    Then verify OTP field is rejecting alphanumeric characters

    When user enters "<incomplete_otp>" as a Otp
    Then validate the "Verify" button is disabled

    When user enters the 6-digit OTP
    Then verify OTP is masked as soon as it is entered
    And validate the "Verify" button is enabled

    When user clicks the back button on the OTP screen
    Then verify user is redirected back to the Forget Password screen
    
    And user enters "<Unregistered Number>" into the mobile number field
    And user enters "<ValidKhmer>" into the fullname field
    Then user click on continue button
    When user enters the 6-digit OTP
    And user clicks on the Verify OTP button
    Then verify error popup with header Invalid is displayed
    And verify error message Transaction has failed due to invalid request. Please try again. is displayed
    And verify retry button is displayed
    When user click on retry button 
    Then verify user is redirected back to the Forget Password screen
    
    When user enters "<8 digit number>" into the mobile number field
    And user enters "<Unregistered KhmerName>" into the fullname field
    Then user click on continue button
    When user enters the 6-digit OTP
    And user clicks on the Verify OTP button
    Then verify error popup with header Invalid is displayed
    And verify error message The mobile number or name entered is invalid. Please enter valid credentials associated with your account and try again. is displayed
    And verify retry button is displayed
    When user click on retry button 
    Then verify user is redirected back to the Forget Password screen

    Examples:
      | ValidKhmer | 8 digit number | InvalidOrRandomOtp | expired_otp | alphabets | Alphanumeric | incomplete_otp | Unregistered Number | Unregistered KhmerName |
      | ឌីពីកា       | 98761234       | 784590             | 111111      | ABCDEF    | Abc123       | 12             | 782910667           | សុខសេរី                  |

  @smoke @resetPasswordVerification
  Scenario Outline: Verify the reset password options
    Given click on Sign In with eSignet
    Then user click on Login with password
    And user click on forget password link

    Then user enters "<ValidKhmer>" into the fullname field
    When user enters "<8 digit number>" into the mobile number field
    Then user click on continue button

    When user enters "<ValidOTP>" as a Otp
    Then user clicks on the Verify OTP button
    And verify user is redirected to the reset password screen

    Then user verify reset password header
    And user verify reset password description
    And user verify new password label
    And user verify confirm new password label
    And user verify new password input text box is present
    And user verify confirm new password input text box is present
    And user verify new password info icon is visible
    Then user click on new password info icon
    And verify new password policy displayed
    Then user verify new password field placeholder "Enter new password"
    And user verify confirm password field placeholder "Confirm new password"
    And verify reset button is disabled

    When user enters "<passwordWithoutNumber>" into the new password field
    And user clicks outside the password field
    Then verify an error message Password does not meet the password policy. is displayed
    
    When user enters "<lessThanEightChar>" into the new password field
    And user clicks outside the password field
    Then verify an error message Password does not meet the password policy. is displayed
    
    When user enters "<moreThan20Char>" into the new password field
    Then verify password input is resitricted to twenty characters
    
    When user enters "<valid NewPassword>" into the new password field
    And verify reset button is disabled
    
    When user enters "<differentPassword>" into the confirm password field
    And user clicks outside the password field
    Then verify an error message New Password and Confirm New Password do not match. is displayed
    And verify reset button is disabled
    
    When user enters "<moreThan20Char>" into the confirm password field
    Then verify confirm password input is resitricted to twenty characters
    
    When user enters "<lessThanEightChar>" into the confirm password field
    And user clicks outside the password field
    Then verify an error message New Password and Confirm New Password do not match. is displayed
    
    Then validate the New Password field is masked
    And verify the Confirm Password field is masked

  	When user clicks on the unmask icon in the New Password field
  	Then validate the New Password field is unmasked

  	When user clicks on the unmask icon in the ConfirmPassword field
  	Then verify the Confirm Password field is unmasked

  	When user clicks again on the unmask icon in the New Password field
  	Then validate the New Password field is masked

  	When user clicks again on the unmask icon in the ConfirmPassword field
  	And verify the Confirm Password field is masked
  	
  	When user enters "<singleChar>" into the new password field
    And user enters "<singleChar>" into the confirm password field
    Then verify reset button is disabled
    
    When user enters "<valid NewPassword>" into the new password field
    And user enters "<validConfirmPassword>" into the confirm password field
    And user clicks on Reset button
    Then verify system display password reset in progress message
    Then verify success screen with header Password Reset Confirmation is displayed
    And verify the message Your password has been reset successfully. Please login to proceed. is displayed
    And verify Login button is displayed
    When user clicks on Login button
    Then verify user is redirected to login screen of relying party
    
    Examples:
      | ValidKhmer | 8 digit number | ValidOTP | passwordWithoutNumber | lessThanEightChar | moreThan20Char         | valid NewPassword | differentPassword | singleChar | validConfirmPassword |
      | ឌីពីកា       | 98761234       | 111111   | abc@Def!              | Ab@12             | ABCDEfghijklm@!1234567 | NewPass@12        | Pass@1234         | A          | NewPass@12           |
      
    
  @smoke @ForgetPasswordOtpNotification
  Scenario Outline: Verify the notification when OTP requested for forgot password
  	Given user opens SMTP portal
  	And navigate back to eSignet portal
  	Given click on Sign In with eSignet
  	Then user click on Login with password
    And user click on forget password link
    Then user enters "<ValidKhmer>" into the fullname field
    When user enters "<ValidNumber>" into the mobile number field
    Then user click on continue button

  	And user switches back to SMTP portal
  	Then verify English language notification Use XXXXXX to verify your KhID account. is received for otp requested
  	And switch back to eSignet portal
  	When user enters the 6-digit OTP
  	And user clicks on the Verify OTP button
  	And user enters "<validNewPassword>" into the new password field
    And user enters "<validConfirmPassword>" into the confirm password field
    And user clicks on Reset button
  	Then user switches back to SMTP portal
  	And verify You successfully changed KhID password. message is displayed
  
  	And navigate back to eSignet portal
  	Given click on Sign In with eSignet
  	Then user click on Login with password
    And user click on forget password link
    Then user clicks on Language Selection Option
    And user selects Khmer from the language dropdown
    Then user enters "<ValidKhmer>" into the fullname field
    And user enters "<ValidNumber>" into the mobile number field
    Then user click on continue button
  	And user switches back to SMTP portal
  	Then verify Khmer language notification ប្រើ XXXXXX ដើម្បីផ្ទៀងផ្ទាត់គណនី KhID របស់អ្នក។ is received for otp requested
  	And switch back to eSignet portal
 	When user enters the 6-digit OTP
  	And user clicks on the Verify OTP button
  	And user enters "<validNewPassword>" into the new password field
    And user enters "<validConfirmPassword>" into the confirm password field
    And user clicks on Reset button
  	Then user switches back to SMTP portal
  	And verify អ្នកបានផ្លាស់ប្ដូរពាក្យសម្ងាត់ KhID ដោយជោគជ័យ។ is displayed
 
	Examples:
  	| ValidKhmer | ValidNumber | validNewPassword | validConfirmPassword | 
  	| ឌីពីកា       | 98761234    | Pass@1234        | Pass@1234            |
  	
  @smoke @BookMarkSignupUrl
  Scenario Outline: Navigate directly using sign-up URL
    Given user directly navigates to sign-up portal URL
    And verify the reset password button is available
    When user click on reset password button
    Then verify it is accessible,user is redirected to the Forget Password screen
    
