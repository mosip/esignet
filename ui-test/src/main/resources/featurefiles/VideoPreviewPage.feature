#@smokeAndRegression
Feature: Esignet Video Preview Page
  This feature file is for verifying the Video Preview page  
  
  @smoke @VideoPreviewPage
  Scenario: Verifying PreVerification Guide And Video Preview Page
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   And clicks on proceed button in next page
   Then select the e-kyc verification provider
   And clicks on proceed button in e-kyc verification provider page
   And user select the check box in terms and condition page
   And user clicks on proceed button in terms and condition page
   Then verify user should be navigated to pre-verification guide or video preview key information screen page
   Then verify header is displayed in pre-verification guide screen page 
   Then verify scrollable present in pre-verification guide screen page
   Then verify list of instructions displayed in pre-verification guide screen page
   Then verify cancel button present in pre-verification guide screen page
   Then verify proceed button present in pre-verification guide screen page
   And clicks on cancel button in pre-verification guide screen page
   Then verify attention popup displayed in pre-verification guide screen page
   And clicks on stay button in pre-verification guide screen page
   Then verify user should be navigated to pre-verification guide or video preview key information screen page
   
   
   And clicks on cancel button in pre-verification guide screen page
   Then verify attention popup displayed in pre-verification guide screen page
   And clicks on discontinue button in pre-verification guide screen page
   
   And clicks on sign in with esignet button in login page
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   And clicks on proceed button in next page
   Then select the e-kyc verification provider
   And clicks on proceed button in e-kyc verification provider page
   And user select the check box in terms and condition page
   And user clicks on proceed button in terms and condition page
   Then verify user should be navigated to pre-verification guide or video preview key information screen page
   Then verify proceed button enable after camera access
   
   When clicks on proceed button in pre-verification guide screen page
   Then verify loading message displayed in pre-verification guide screen page