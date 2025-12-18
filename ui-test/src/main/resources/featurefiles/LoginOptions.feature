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