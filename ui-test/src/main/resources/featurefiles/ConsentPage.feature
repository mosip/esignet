#@smokeAndRegression
Feature: Esignet Consent Page
  This feature file is for verifying the Consent page

  @smoke @loginOptionsPageVerification
  Scenario: Verify the Login options page
    Given click on Sign In with eSignet
    Then validate that the logo is displayed
    #And validate the logo alignment
    #And validate that header is displayed
    #And validate that sub-header is displayed
    #And I validate the outcomes
    #And check more outcomes
    
  @NeedsUIN @UinAndOtpAuthentication
  Scenario Outline: Verifying flow for Otp based authentication using UIN
    Given click on Sign In with eSignet  
    When Click on Language selection option
    And Select the mandatory language
    Then user click on Login with Otp
    And user selects UIN or VID option
   
    When user enter the single_char UIN in the UINorVID text field
    Then verify get otp button is enabled
    And user click on get otp button
    Then verify Please Enter Valid Individual ID error is displayed
   
    When user enter the invalid UIN in the UINorVID text field
    And user click on get otp button
    Then verify Please Enter Valid Individual ID error is displayed
   
    When user enter the valid UIN in the UINorVID text field
    And user click on get otp button
    Then verify user is navigated to Otp page
    And validate VerifyOtp button is disabled
    Then verify OTP timer starts and keeps decreasing
   
    When user enters the "<Partial Otp>"
    And validate VerifyOtp button is disabled
   
    When user enters the "<invalid Otp>"
    And validate VerifyOtp button is enabled
    And click on verify Otp button
    Then verify OTP authentication failed.Please try again. error is displayed 
   
    And user enters the "<valid Otp>"
    When user click on back button in otp screen
    Then verify user is redirected to Login screen of eSignet
  
    And user selects UIN or VID option
    When user enter the valid UIN in the UINorVID text field
    And user click on get otp button
    And user enters the "<valid Otp>"
    And validate VerifyOtp button is enabled 
  
    Given user launches SMTP portal
    And navigate to eSignet portal
    Given click on Sign In with eSignet  
    Then user click on Login with Otp
    And user selects UIN or VID option
    When user enter the valid UIN linked with both mobileNumber and email
    And user click on get otp button
    And user switches back to SMTP portal
    Then user should get OTP on the registered mobileNumber and email

Examples:
  | Partial Otp | invalid Otp | valid Otp |
  | 1111        | 123456      | 111111    |
  
  
  @NeedsVID @VidAuthentication
  Scenario Outline: Verifying login flow authentication using VID
    Given click on Sign In with eSignet  
    When Click on Language selection option
    And Select the mandatory language
    Then user click on Login with Otp
    And user selects UIN or VID option
   
    When user enter the single_char VID in the UINorVID text field
    Then verify get otp button is enabled
    And user click on get otp button
    Then verify Please Enter Valid Individual ID error is displayed
   
    When user enter the invalid VID in the UINorVID text field
    And user click on get otp button
    Then verify Please Enter Valid Individual ID error is displayed
   
    When user enter the valid VID in the UINorVID text field
    And user click on get otp button
    Then verify user is navigated to Otp page
    And user enters the "<valid Otp>"
    And validate VerifyOtp button is enabled
    When user click on back button in otp screen
    Then verify user is redirected to Login screen of eSignet
  
    Given user launches SMTP portal
    And navigate to eSignet portal
    Given click on Sign In with eSignet  
    Then user click on Login with Otp
    And user selects UIN or VID option
    When user enter the valid VID linked with both mobileNumber and email
    And user click on get otp button
    And user switches back to SMTP portal
    Then user should get OTP on the registered mobileNumber and email

Examples:
  | valid Otp |
  | 111111    |