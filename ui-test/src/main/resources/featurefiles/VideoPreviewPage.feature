#@smokeAndRegression
Feature: Esignet Video Preview Page
  This feature file is for verifying the Video Preview page

# Not in Go — mark NA (Video eKYC / PreVerification / Pre_Video_Preview / Khmer Video Preview —
# classic eKYC provider-selection flow does not exist in esignet-go/Thunder, so these screens
# are unreachable)
#   @smoke @VideoPreviewPage
#   Scenario: Verifying PreVerification Guide And Video Preview Page
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify key information header is displayed in video preview screen page
#    Then verify scrollable present in video preview screen page
#    Then verify list of instructions displayed in video preview screen page
#    Then verify cancel button present in video preview screen page
#    Then verify proceed button present in video preview screen page
#    And clicks on cancel button in video preview screen page
#    Then verify attention warning popup displayed in video preview screen page
#    And clicks on stay button in attention warning popup
#    Then verify user should be navigated to video preview screen page

#    And clicks on cancel button in video preview screen page
#    Then verify attention warning popup displayed in video preview screen page
#    And clicks on discontinue button in attention warning popup

#    And clicks on sign in with esignet button in login page
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    And user disconnects the network
#    Then verify eKYC error message is displayed
#    And clicks on okay button in eKYC error screen
#    And user reconnects the network

#    And clicks on sign in with esignet button in login page
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user starts monitoring slot availability requests
#    And user clicks on proceed button in camera preview page
#    Then verify loading screen message displayed in video capture screen page
#    Then verify liveness check screen is displayed
#    And verify slot availability is checked every 6 seconds for a maximum of 10 attempts
#    And verify user is able to view themselves on video eKYC screen
#    And user disconnects the network
#    Then verify network dropped message is displayed
#    And user reconnects the network

#   @smoke @VideoPreviewPage @cameraPrompt
#   Scenario: TC_Pre_Video_Preview_04 - Verify camera permission is in prompt state when access has not yet been decided
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify camera permission state is "prompt"

#   @smoke @VideoPreviewPage @cameraDenied
#   Scenario: TC_Pre_Video_Preview_05 - Verify camera access disabled message and Proceed button state when camera access is denied
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify camera access disabled message is displayed
#    And verify camera access disabled subtitle is displayed
#    And verify proceed button is disabled in video preview screen page

#   @smoke @VideoPreviewPage @cameraDenied
#   Scenario: TC_Pre_Video_Preview_07 - Verify prompt does not reappear after denial and camera works once re-enabled from browser settings
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify camera permission state is "denied"
#    And user grants camera access from browser settings
#    And user refreshes the browser
#    Then verify proceed button enable after camera access

#   @smoke @VideoPreviewPage @leaveSitePrompt
#   Scenario: Verify the "Leave site?" prompt on browser back and refresh from the Video Preview screen
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page

#    And user navigates back in the browser and a leave site prompt should appear
#    And user cancels the leave site prompt
#    Then verify user should be navigated to video preview screen page

#    And user refreshes the browser and a leave site prompt should appear
#    And user cancels the leave site prompt
#    Then verify user should be navigated to video preview screen page

#    And user refreshes the browser and a leave site prompt should appear
#    And user confirms the leave site prompt
#    Then verify user is no longer on the video preview screen

#    And clicks on sign in with esignet button in login page
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page

#    And user navigates back in the browser and a leave site prompt should appear
#    And user confirms the leave site prompt
#    Then verify user is no longer on the video preview screen

#   @mobile @VideoEkycMobileResponsive @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_29 - Verify Video eKYC verification screen UI is mobile responsive
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify user is able to view themselves on video eKYC screen
#    Then verify video eKYC screen is mobile responsive

#   @smoke @VideoEkycLanguageSwitch @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_33 - Verify by changing the language to Khmer and vice versa in video eKYC screen
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed

#    And user captures the video eKYC screen content before language change
#    And user clicks on language dropdown in video eKYC screen
#    And user selects khmer language in video eKYC screen
#    Then verify video eKYC screen content and language are updated to khmer

#    And user captures the video eKYC screen content before language change
#    And user clicks on language dropdown in video eKYC screen
#    And user selects the mandatory language in video eKYC screen
#    Then verify video eKYC screen content and language are reverted to the mandatory language

#   @smoke @VideoPreviewKhmerLanguage
#   Scenario: Verify Video Preview screen after logging in with Khmer language selected
#    When click on Language selection option
#    And select the khmer language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify key information header is displayed in video preview screen page
#    Then verify list of instructions displayed in video preview screen page
#    Then verify cancel button present in video preview screen page
#    Then verify proceed button present in video preview screen page
#    Then verify video preview screen content is displayed in khmer language

#   @smoke @VideoEkycCameraSessionTimeout @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_05 - Verify by disabling camera access on Video eKYC screen
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed

#    And user disables camera access on video eKYC screen
#    Then verify session timeout message is displayed after disabling camera access
#    And user enables camera access on video eKYC screen
#    Then verify user is redirected to relying party with verification incomplete error

#   @smoke @VideoEkycStatusScreens @ES-1003
#   Scenario: TC_Video_eKYC_Status_Screens_01 - Verify the text labels, elements in Video eKYC screen
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify user is able to view themselves on video eKYC screen
#    And verify onscreen instructions are displayed above the video frame on video eKYC screen
#    And verify the logo is displayed on video eKYC screen
#    And verify the language dropdown is displayed on video eKYC screen
#    And verify the powered by eSignet footer is displayed on video eKYC screen

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_02 - Verify that users can view themselves on Video eKyC screen
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify user is able to view themselves on video eKYC screen

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_06 - Verify onscreen instructions provided above video frame
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify the welcome countdown message is displayed above the video frame on video eKYC screen

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_07 - Verify onscreen instructions above the video frame are aligned properly
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify onscreen instructions are displayed above the video frame on video eKYC screen
#    And verify instructions and video frame are aligned properly on video eKYC screen

#   @smoke @VideoEkycColorFrameVerification @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_08 - Verify solid colors and instructions are displayed correctly on the Video eKYC screen for color-based frame verification
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify solid colors are displayed across the full screen on video eKYC screen
#    And verify instructions and video frame are aligned properly on video eKYC screen

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_09 - Verify the liveliness Passive verification via color verification
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify solid colors are displayed across the full screen on video eKYC screen

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_10 - Verify the liveliness active verification via onscreen instructions
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_28 - Verify disconnecting internet in video eKYC verification
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And user disconnects the network
#    Then verify network dropped message is displayed
#    And user reconnects the network

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_30 - Verify by navigating to video eKYC screen in English language
#    When click on Language selection option
#    And select the english language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify video eKYC screen content is displayed in english language

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_31 - Verify by navigating to video eKYC verification screen in Khmer language
#    When click on Language selection option
#    And select the khmer language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify video eKYC screen content and language are updated to khmer

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_32 - Verify the language dropdown is accessible
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    Then verify liveness check screen is displayed
#    And verify the language dropdown is displayed on video eKYC screen
#    And user clicks on language dropdown in video eKYC screen
#    Then verify the language dropdown is expanded on video eKYC screen
#    And user clicks on language dropdown in video eKYC screen
#    Then verify the language dropdown is collapsed on video eKYC screen

#   @smoke @VideoEkycStatusScreens
#   Scenario: TC_Video_eKYC_Status_Screens_34 - Verify the error message, when KYC verification fails and KYC provider do not allow retrials
#    When click on Language selection option
#    And select the mandatory language
#    And user click on Login with Otp
#    Then user enters Registered mobile number into the mobile number field
#    And user click on get otp button
#    When user enters the correct otp
#    And click on verify Otp button

#    Then verify consent should ask user to proceed in attention page
#    And clicks on proceed button in attention page
#    And clicks on proceed button in next page
#    Then select the e-kyc verification provider
#    And clicks on proceed button in e-kyc verification provider page
#    And user select the check box in terms and condition page
#    And user clicks on proceed button in terms and condition page
#    Then verify user should be navigated to video preview screen page
#    Then verify proceed button enable after camera access
#    And user clicks on proceed button in camera preview page
#    And user disconnects the network
#    Then verify eKYC failure popup shows header "Verification Unsuccessful", message "Oops! We were unable to complete the eKYC verification" and button "OKAY"
#    And clicks on okay button in eKYC error screen
#    And user reconnects the network
