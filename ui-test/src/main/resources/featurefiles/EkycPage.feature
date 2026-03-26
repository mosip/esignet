@smokeAndRegression
Feature: Esignet eKyc Page
  This feature file is for verifying the eKyc page  

  @smoke @eKycStepsPageVerification
  Scenario Outline: Verify eKYC process steps screen content
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the "<correct Otp>"
   And click on verify Otp button
   
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC Process Steps screen
   And user verify the title of step 1 is Choose an eKYC provider
   And user verify that the subtitle of step 1 is displayed in eKYC Process Steps screen
   And user verify the title of step 2 is Terms And Conditions
   And user verify that the subtitle of step 2 is displayed in eKYC Process Steps screen
   
Examples:
  | correct Otp  |
  | 111111       |
    