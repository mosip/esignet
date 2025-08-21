@smokeAndRegression
Feature: Esignet SignUpFeatures page
  This Feature is for SignUpFeatures page

  @smoke @signupfeatures
  Scenario Outline: Verify SignUpFeatures feature
    Given user is on relying party portal
    Then user clicks on login button in relying portal page
    Then user verify that Sign-Up with unified login button should display
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
    | 8 digit number | 9 digit number | 8 digit number start with 0 | 9 digit number start with 0 | 9 zero     | special character | alpha numeric character | less than 8 digit |
    | 98345678       | 907345789      | 01245678                    | 0123456789                  | 000000000 | !@$$@*            | abc123                  | 98567             |
  @smoke @OtpPage
  Scenario Outline: OTP input acceptance and verify button state
    Given user clicks on login button in relying portal page
    Then user clicks on Sign-Up with unified login button hyperlink
    Then user enters "<already registered number>" in the mobile text field
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
    And user clicks on verify OTP button
    Then verify an error message OTP expired. Please request a new one and try again. is displayed at the top check it
    When user click on the close icon of the error message
    Then verify the error message is not display
    
    Then user clicks on Resend OTP button
    
    
    
    
    
    
    
    
    
    
    
    
   Examples:
    |already registered number| expired OTP |
    | 98545678                | 111111      |