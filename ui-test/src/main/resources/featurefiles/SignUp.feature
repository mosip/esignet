@smokeAndRegression
Feature: Esignet Signup page

  @smoke @signUpPageVerification
  Scenario: Verify user can sign up using mobile number and OTP

    Given click on Sign In with eSignet
    When click on signup link
    Then validate that the logo is displayed
    And enter phone number
    And click on continue button
    And user enters OTP
    And click on verify otp button
