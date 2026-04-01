@smokeAndRegression
Feature: Esignet eKyc Page
  This feature file is for verifying the eKyc page  
  
 @smoke @eKycStepsPageVerification
 Scenario: Verify eKYC process steps screen content
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC process steps screen
   And user verify the title of step 1 is choose an eKYC provider
   And user verify that the subtitle of step 1 is displayed in eKYC process steps screen
   And user verify the title of step 2 is terms and conditions
   And user verify that the subtitle of step 2 is displayed in eKYC process steps screen
   And user verify the title of step 3 is pre verification guide
   And user verify that the subtitle of step 3 is displayed in eKYC process steps screen
   And user verify the title of step 4 is identity verification
   And user verify that the subtitle of step 4 is displayed in eKYC process steps screen
   And user verify the title of step 5 is review consent
   And user verify that the subtitle of step 5 is displayed in eKYC process steps screen
   And user verify the cancel button is visible in eKYC process steps screen
   And user verify the cancel button is clickable in eKYC process steps screen
   Then user verify warning popup is displayed on clicking cancel button
   And user verify the header is attention in warning popup
   And user verify the message is displayed in warning popup
   And user verify the stay button is visible in warning popup
   And user verify the discontinue button is visible in warning popup
   When user verify the stay button is clickable in warning popup
   And user verify warning popup disappeared
   Then verify user is redirected back to ekycScreen
   And user click on cancel button in eKYC process steps screen
   When user verify the discontinue button is clickable in warning popup
   Then user verify user is redirected to relying party login page
   When user clicks on sign in with esignet button
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC process steps screen
   And user verify the proceed button is visible in eKYC process Steps screen
   When user verify the proceed button is clickable in eKYC process steps screen
   Then user verify user is redirected to list of eKYC providers screen

  
 @smoke @ListOfEkycServiceProvider
 Scenario: Verify list of eKYC service provider screen
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC process steps screen
   When user verify the proceed button is clickable in eKYC process steps screen
   Then user verify user is redirected to list of eKYC providers screen
   Then user verify the header title in list of eKYC providers screen
   And user verify the specific eKYC provider names are visible in list of eKYC providers screen
   And user verify foundational ID one and ID two are displayed in list of eKYC providers screen
   And user verify proceed button is disabled when no eKYC provider is selected in list of eKYC providers screen
   And user verify disabled proceed button is not clickable in list of eKYC providers screen
   And user verify the cancel button is visible in the list of eKYC providers screen
   And user verify the cancel button is clickable in the list of eKYC providers screen
   Then user verify warning popup is displayed on clicking cancel button in list of eKYC providers screen
   And user verify the header in warning popup in list of eKYC providers screen
   And user verify the message displayed in warning popup in list of eKYC providers screen
   And user verify the stay button is visible in warning popup in list of eKYC providers screen
   And user verify the discontinue button is visible in warning popup in list of eKYC providers screen
   When user verify the stay button is clickable in warning popup in list of eKYC providers screen
   And user verify the cancel button is clickable in the list of eKYC providers screen
   When user verify the discontinue button is clickable in warning popup in list of eKYC providers screen
   Then user verify user is redirected to relying party login page
   When user clicks on sign in with esignet button
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC process steps screen
   And user verify the proceed button is visible in eKYC process Steps screen
   When user verify the proceed button is clickable in eKYC process steps screen
   Then user verify user is redirected to list of eKYC providers screen
   And user verify the specific eKYC provider names is clickable in list of eKYC providers screen
   And user verify the proceed button is clickable in list of eKYC providers screen
   Then user verify user is redirected to terms and conditions screen

 @smoke @TermsAndConditionsScreen
 Scenario: Verify terms and conditions screen
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC process steps screen
   When user verify the proceed button is clickable in eKYC process steps screen
   Then user verify user is redirected to list of eKYC providers screen
   And user verify the specific eKYC provider names is clickable in list of eKYC providers screen
   And user verify the proceed button is clickable in list of eKYC providers screen
   Then user verify user is redirected to terms and conditions screen
   And user verify the header title displayed in terms and conditions screen
   And user verify the sub header message displayed in terms and conditions screen
   And user verify the content displayed in terms and conditions screen
   And user verify the text beside checkbox message displayed in terms and conditions screen
   And user verify content body text frame has scrollbar enabled in terms and conditions screen
   And user verify checkbox is not selected by default in terms and conditions screen
   And user verify proceed button is disabled when no check box is selected in terms and condition screen
   And user click on checkbox in terms and conditions screen
   And user verify the cancel button is visible in terms and conditions screen
   And user verify the cancel button is clickable in terms and conditions screen
   Then user verify warning popup is displayed on clicking cancel button in terms and conditions screen
   When user verify the stay button is clickable in warning popup in terms and conditions screen
   And user verify warning popup disappeared
   Then verify user is redirected back to terms and conditions screen
   And user verify the cancel button is clickable in terms and conditions screen
   When user verify the discontinue button is clickable in warning popup in terms and conditions screen
   Then user verify user is redirected to relying party login page
   When user clicks on sign in with esignet button
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC process steps screen
   When user verify the proceed button is clickable in eKYC process steps screen
   Then user verify user is redirected to list of eKYC providers screen
   And user verify the specific eKYC provider names is clickable in list of eKYC providers screen
   And user verify the proceed button is clickable in list of eKYC providers screen
   Then user verify user is redirected to terms and conditions screen
   And user click on checkbox in terms and conditions screen
   And user verify the proceed button is enabled after selecting check box in terms and conditions screen
   And user verify the proceed button is displayed in terms and condition screen
   And user clicks on proceed button in terms and condition page
   Then verify user should be navigated to video preview screen page