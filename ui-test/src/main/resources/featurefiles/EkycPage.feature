#@smokeAndRegression
Feature: Esignet eKyc Page
  This feature file is for verifying the eKyc page  

  @smoke @eKYCOptionsPageVerification
  Scenario Outline: Verifying Toggle button in consent screen
   When Click on Language selection option
   And Select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the "<correct Otp>"
   And click on verify Otp button
   
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then Verify user navigate to eKYC Process Steps screen
   And Verify the title of step 1 is Choose an eKYC provider
   And Verify that the subtitle of step 1 is displayed in eKYC Process Steps screen
   And Verify the title of step 2 is Choose an eKYC provider
   And Verify that the subtitle of step 2 is displayed in eKYC Process Steps screen

Examples:
  | correct Otp  |
  | 111111       |
    