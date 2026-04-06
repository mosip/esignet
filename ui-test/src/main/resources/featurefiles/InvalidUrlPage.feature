@smokeAndRegression
Feature: Invalid Url Page
  This feature file is for verifying the Invalid Url page  
    
  @smoke @InvalidEsignetUrlPage
  Scenario Outline: Verifying invalid esignet url page not found accessibility flow
   Given user captures the authorize url
   When user modifies domain in the esignet url
   Then verify this site can’t be reached error is displayed
   When user modify the nonce value in esignet url
   Then verify user remain on same esignet page without any error
   When user modify the hash value in the esignet url
   Then verify unable to process Please try again error is displayed
   When user change the language to "<kannada>" from dropdown
   Then verify "<error>" message is displayed in chosen language
   When user remove the nonce and state value in esignet url
   And verify user remain on same esignet page without any error
   When user modify the state value in esignet url
   And verify user remain on same esignet page without any error
   When user modify the login value in esignet url
   Then verify the page you are looking for does not exist error is displayed
   When user remove the login value in esignet url
   And verify the page you are looking for does not exist error is displayed
   When user modifies authorize value in esignet url
   And verify the page you are looking for does not exist error is displayed
   When user removes authorize value in esignet url
   And verify the page you are looking for does not exist error is displayed
   Given user relaunches esignet url
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   And verify user navigated to attention screen
   When user modify the claims details value in esignet url
   And verify the page you are looking for does not exist error is displayed
   When user remove the claim details value in signup url
   And verify the page you are looking for does not exist error is displayed
   
Examples:
   | kannada | error                   |
   |  ಕನ್ನಡ   | ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲು ಸಾಧ್ಯವಾಗುತ್ತಿಲ್ಲ. |
 
 
  @smoke @InvalidSignupUrlPage
  Scenario: Verifying invalid signup url page not found accessibility flow
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent should ask user to proceed in attention page
   And clicks on proceed button in attention page
   And verify user navigate to eKYC process steps screen
   When user modify the identity verification value in esignet url
   Then verify error screen along with reset password button and register button is displayed
   When user remove the identity verification value in esignet url
   And verify error screen along with reset password button and register button is displayed
   Given user directly navigates to sign-up portal URL
   When user modifies domain in the signup url
   Then verify this site can’t be reached error is displayed
   And user directly navigates to sign-up portal URL
   And user clicks on Register button
   When user modifies the signup value in signup url
   Then verify error screen along with reset password button and register button is displayed
   And user clicks on Register button
   When user remove the signup value in signup url
   And verify error screen along with reset password button and register button is displayed
   And user click on reset password button
   When user modifies the reset password value in signup url
   And verify error screen along with reset password button and register button is displayed
   And user click on reset password button
   When user remove the reset password value in signup url
   And verify error screen along with reset password button and register button is displayed
   When user navigates to something went wrong page
   Then verify something went wrong our experts are working hard to make things working again error message displayed
   When user remove the something went wrong value in signup url
   And verify error screen along with reset password button and register button is displayed
   
  @smoke @InvalidConsentPage
  Scenario: Verifying invalid consent url page not found accessibility flow
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
   And user clicks on proceed button in camera preview page
   And user is navigated to consent screen once liveness check completes
   And verify user is navigated to consent screen
   When user modify the consent value in esignet url
   And verify the page you are looking for does not exist error is displayed
   When user remove the consent value in esignet url
   And verify the page you are looking for does not exist error is displayed
