@smokeAndRegression
Feature: Esignet Signup page

  @smoke @signUpPageVerification
  Scenario: Verify the Esignet Signup page

    Given click on Sign In with eSignet
    When click on signup link
    Then validate that the logo is displayed
    #And click on ble tab
    #And verify information message on ble verification