@smokeAndRegression
Feature: Esignet Signup page

  @smoke @signUpPageVerification
  Scenario: Verify the Esignet Signup page
    Given user directly navigates to sign-up portal URL
    And user clicks on Register button
    Then the registration form fields should be displayed