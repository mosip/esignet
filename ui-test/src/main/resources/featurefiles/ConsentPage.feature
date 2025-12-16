#@smokeAndRegression
Feature: Esignet Consent Page
  This feature file is for verifying the Consent page

  @NeedsUIN @UinAndOtpAuthentication
  Scenario Outline: Verifying flow for Otp based authentication using UIN
    #Given click on Sign In with eSignet  
    #When Click on Language selection option
    #And Select the mandatory language
    Then user click on Login with Otp
    And user selects UIN or VID option
   
    When user enter the single_char UIN in the UINorVID text field
    Then verify get otp button is enabled
    And user click on get otp button