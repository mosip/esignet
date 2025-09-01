@smokeAndRegression
Feature: Esignet SignUpFeatures page
  This Feature is for SignUpFeatures page

  @smoke @signupfeatures
  Scenario Outline: Verify SignUpFeatures feature
    Given user is on relying party portal
    Given user clicks on login button in relying portal page
    Then user redirect and verify that Sign-Up with unified login button should display
    Then user clicks on Sign-Up with unified login button hyperlink
    Then user will redirected to mobile number registration page
    Then user verify Header text in same page
    And user verify Enter 8-9 digit number is visible
    Then user verify continue button is visible
    Then user will check previous navigate arrow is visible
    Then user verify for preferred language option is visible
    Then user verify footer text powered by esignet logo display
    Then user verify mobile number text should prefilled with country code

    When user enters "<8 digit number>" in the mobile text field
    And user tabs out on mobile field

    When user enters "<9 digit number>" in the mobile text field
    And user tabs out on mobile field

    When user enters "<less than 8 digit>" in the mobile text field
    Then user tabs out on mobile field
    And validate that Enter a valid username error massage should display
    And continue button remains disabled

    When user enters "<8 digit number start with 0>" in the mobile text field
    And user tabs out on mobile field
    And validate that Number cannot start with zero. Enter a valid mobile number. massage will display

    When user enters "<9 digit number start with 0>" in the mobile text field
    Then user tabs out on mobile field
    And validate that Number cannot start with zero. Enter a valid mobile number. massage will display

    When user enters "<9 zero>" in the mobile text field
    Then user tabs out on mobile field
    And validate that Number cannot start with zero. Enter a valid mobile number. massage will display

    When user enters "<special character>" in the mobile text field
    When user enters "<alpha numeric character>" in the mobile text field
    When user clicks on navigate back button
    Then user will redirected to previous screen
    Then user clicks on Sign-Up with unified login button hyperlink
    When user clicks on browser back button
    Then user will redirected to previous screen

  Examples:
    | 8 digit number | 9 digit number | 8 digit number start with 0 | 9 digit number start with 0 | 9 zero    | special character | alpha numeric character | less than 8 digit |
    | 98345678       | 907345789      | 01245678                    | 0123456789                  | 000000000 | !@$$@*            | abc123                  | 98567             |

  @smoke @OtpPage
  Scenario Outline: OTP input acceptance and verify the state
    Given user clicks on login button in relying portal page
    Then user clicks on Sign-Up with unified login button hyperlink
    Then user enters "<valid mobile number>" in the mobile text field
    Then user clicks on continue button
    Then user navigated to enter OTP header screen

    Given user is on OTP screen
    Then user navigated to enter OTP header screen
    Then user verifies the OTP screen description should contain a masked mobile number verify it
    And user verify OTP input field is visible
    Then user verifies the verify OTP field is visible
    Then user verify the time count down for 3 minutes
    Then user verifies for Resend OTP is visible
    Then user go back and update the mobile number

    When user clicks on back button on OTP screen
    Then user will redirected to mobile number registration page
    Then user enters "<already registered number>" in the mobile text field
    Then user clicks on continue button
    Then user navigated to enter OTP header screen

    Then user waits for OTP time expire
    Then user enters "<expired OTP>" in the OTP field screen
    And user check that verify button comes to enable state
    And user clicks on verify OTP button quickly
    When user click on the close icon of the error message
    Then verify the error message is not display

    Then user waits for OTP time expire
    When user enters "<invalid OTP>" in the OTP field screen
    And user clicks on verify OTP button quickly
    And verify an error massage OTP expired. Please request a new one and try again

    When user enters "<specialCharacter>" in the OTP field screen
    Then user verify that OTP field rejecting specialCharacter

    When user enters "<alphabet>" in the OTP field screen
    Then user verify that OTP field rejecting alphabet

    When user enters "<alphaNumericCharacter>" in the OTP field screen
    Then user verify that OTP field rejecting alphaNumericCharacter

    When user enters "<incompleteOTP>" in the OTP field screen
    When user clicks on back button on OTP screen
    Then user will redirected to mobile number registration page
    Then user enters "<valid mobile number>" in the mobile text field
    Then user clicks on continue button
    Then user navigated to enter OTP header screen
    Then user enters "<complete 6 digit OTP>" in the OTP field
    And user clicks on verify OTP button quickly
    And user verify that Successful! is display
    And user verify that Your mobile number has been verified successfully massage will showing
    Then user verify that continue button display in success screen

  Examples:
    | already registered number | expired OTP | invalid OTP | specialCharacter | alphabet | incompleteOTP | valid mobile number | alphaNumericCharacter | complete 6 digit OTP |
    | 98545678                  | 111111      | 000000      | @#!$%^           | asdfg    | 123           | 983454566           | abc123                | 111111               |

  @smoke @accountSetupPage
  Scenario Outline: Complete Registration Process
    Given user clicks on login button in relying portal page
    Then user redirect and verify that Sign-Up with unified login button should display
    Then user clicks on Sign-Up with unified login button hyperlink
    Then user enters "<new number>" in the mobile text field
    Then user clicks on continue button
    Then user navigated to enter OTP header screen
    Then user enters "<expired_OTP>" in the OTP field
    And user clicks on verify OTP button quickly
    And user will redirected to successful screen
    And user clicks on continue button in successful screen
    Then user verify in header Setup Account display
    Then user verify Please enter the requested details to complete your registration display
    Then user verify that Username field will display
    And user verify that enter fullname in Khmer display
    And user verify setup Password will display
    And user verify option to confirm password display
    Then user verify checks to terms and condition privacy policy
    Then user verify continue button in setupAccount page
    Then user enters "<ValidFullNameInKhmer>" in the setupAccount page
    Then user enters "<ValidPassword>" in the same page
    Then user enters "<ConfirmPassword>" in account screen
    Then user clicks on CheckBox of terms and condition and privacy policy in setupAccount
    Then continue button remains enabled in setup page
    Then user clicks on continue button in same screen
    Then it will redirected to congratulations page
    Then user clicks on login button in congratulations page
    Then user redirect and verify that Sign-Up with unified login button should display
    Then user clicks on Sign-Up with unified login button hyperlink
    Then user will redirected to mobile number registration page
    Then user enters "<already_registered_number>" in the mobile text field
    Then user clicks on continue button
    Then user navigated to enter OTP header screen
    Then user enters "<expired_OTP>" in the OTP field screen
    And user clicks on verify OTP button quickly
    Then it will display in header Sign-Up Failed!
    Then provided mobile number is already registered Please use the Login option to proceed will show
    Then ok button will display on same page

  Examples:
    | ValidFullNameInKhmer | ValidPassword | ConfirmPassword | already_registered_number | expired_OTP | new number |
    | អរគុណ                | Pass@2004     | Pass@2004       | 34180006                  | 111111      | 34180006   |
