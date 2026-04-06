#@smokeAndRegression
Feature: Esignet Consent Page
  This feature file is for verifying the Consent page  

 @smoke @registrationProcess
  Scenario: Verify user completes registration process
    Given user directly navigates to sign-up portal URL
    And user clicks on Register button
    Then user enters mobile_number in the mobile number field
    And user clicks on the Continue button
    When user enters the OTP
    And user clicks on the Verify OTP button 
    Then user click on Continue button in Success Screen
    And user fills the signup form using UI specification
    And user clicks on Continue button in Setup Account Page
    And verify that success screen is displayed
    
  @smoke @ToggleButtonInConsentPage
  Scenario: Verifying Toggle button in consent screen
   Given user captures the authorize url
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
   And user clicks on proceed button in camera preview page
   And user is navigated to consent screen once liveness check completes
   And verify user is navigated to consent screen
   And verify the timer starts from 55sec in the consent page via Otp login
   And refresh the browser tab and verify timer continue with leftover seconds
  
   And user clicks on language dropdown button
   And user selects arabic language
   Then verify screen is displayed in RTL format
   When click on Language selection option
   And select the mandatory language
   And verify the tooltip message for Voluntary Claims info icon
   Then verify essential claims are listed separately
   And verify voluntary claims are listed separately
   
   Then verify master toggle should be visible for Voluntary Claims if multiple claims are present
   And verify all toggle buttons for Voluntary Claims are disabled by default
   
   Then verify if user enables Master toggle,all sub-toggles should be enabled
   And if user deselect one of the Voluntary Claims 
   Then verify remaining Voluntary Claims stays selected along with master toggle
   And if user disables Master toggle,all sub-toggles should be disabled
   
   Then verify if user enables Master toggle,all sub-toggles should be enabled
   And if user manually deselects all sub-toggles,verify master toggle also gets disabled
   
   When user enables only one of the Voluntary Claims toggle
   Then verify that the master toggle remains in unselected state
   
   When user enables all the voluntary claims sub-toggle manually
   Then verify that the master toggle is enabled automatically
    
  @smoke @Consentscreen
  Scenario: Verifying Consent Screen changes to handle unavailable voluntary claims
   Given user captures the authorize url
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
   And user clicks on proceed button in camera preview page
   And user is navigated to consent screen once liveness check completes
   And verify user is navigated to consent screen
   
   Then user verify the header of essential claims
   And user verify the list of essential claims are present 
   
   And user verify the action message in consent screen
   And user verify the timer is displayed in consent screen
   
  @smoke @ConsentToUpdateProfile
  Scenario: Verifying user consent to proceed with profile update
   Given user captures the authorize url
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify user is navigated to consent to profile update screen
   And verify the header Attention in the consent to profile update screen
   And verify the sub header in the consent to profile update screen
   And verify the essential claim header in consent to update profile screen
   And verify info icon is available in consent to update profile screen
   And verify proceed button is visible in consent to update profile screen
   And verify cancel button is visible in consent to update profile screen
   And user verify the essential claims list
   And user verify the voluntary claims list
   And user click on essential claim info icon
   And verify the essential claim information displayed on clicking the info icon
   And user tab outside the info icon
   And user click on voluntary claim info icon
   And verify the voluntary claim information displayed on clicking the info icon
   And user tab outside the info icon
   And verify the message click on proceed to begin with the verification process is displayed below
   When user click on cancel button in consent update to profile screen
   Then verify warning popup with header attention is displayed
   And verify the sub header in warning popup is displayed
   And verify stay button is available in the warning popup
   And verify discontinue button is available in the warning popup
   When user click on stay button in warning popup
   Then verify user is navigated to consent to profile update screen
   And user click on cancel button in consent update to profile screen
   When user click on discontinue button in warning popup screen
   Then user verify user is redirected to relying party login page
   When user clicks on sign in with esignet button
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   And clicks on proceed button in attention page
   Then verify user navigate to eKYC process steps screen