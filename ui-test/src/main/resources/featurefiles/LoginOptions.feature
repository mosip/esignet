Feature: Esignet Login Options Page
  This feature file is for verifying the Login options page

  @smoke @AuthenticaionPage
  Scenario Outline: Verifying Login page Options
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   Then verify IDP UI uses default language configured in env-config
   Then verify dropdown language selection is present
   And verify multiple options for login is available
   And authentication screen should show login options based on acr_values from url
   And verify more ways to signIn option is available
   When user selects "<other lang>" from the language dropdown
   Then verify the UI is displayed in selected language "<text>"
   When click on Language selection option
   And select the mandatory language

  Examples:
   | other lang | text   |
   | हिन्दी        | लॉगिन    |

  @mobile @mobileViewFeatures
  Scenario: Verifying the UI in mobile view
   When user triggers the authorization endpoint, the response should have status code 200 and contain valid HTML with JS content
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   And click on Language selection option
   And select the mandatory language
   Then user views the portal on multiple screen sizes
   And user verifies the behavior after resizing the browser window to different dimensions
   And user verify the otp button remain visible and aligned after resizing
   Then verify dropdown language selection is present
   And verify multiple options for login is available
   And verify more ways to signIn option is available
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button

   @smoke @supportOfPrefixAndPostfix @MOSIP-22717
  Scenario: Verifying support for multiple prefix and postfix type for the individual ID
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then verify mobile number option is present for authentication
   Then verify nrc id option is present for authentication
   Then verify vid option is present for authentication
   Then verify email option is present for authentication
   Then verify mobile number selected for authentication
   And clicks on prefix number button in authentication screen page
   Then verify khm country prefix displayed for mobile number
   Then verify ind country prefix displayed for mobile number
   And clicks on prefix number button in authentication screen page
   Then user enters Registered mobile number into the mobile number field
   Then verify get otp button is enabled in authentication screen
   And user click on get otp button
   Then verify user navigate to verify otp screen
   When user enters the correct otp
   And click on verify Otp button
   Then verify user navigate to Attention screen
   And user completes consent flow through eKYC and returns to relying party
   And clicks on sign in with esignet button in login page
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then clicks on vid option button in authentication screen page
   When user enters prerequisite vid1 into vid field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   And user completes consent flow through eKYC if attention screen is displayed
   And clicks on sign in with esignet button in login page
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then clicks on vid option button in authentication screen page
   When user enters invalid vid into vid field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into vid field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into vid field
   Then clicks on email option button in authentication screen page
   When user enters invalid email into email field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into email field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into email field
   And clicks on sign in with esignet button in login page
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then clicks on vid option button in authentication screen page
   When user enters prerequisite vid2 into vid field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent is not requested after authentication
   And clicks on sign in with esignet button in login page
   When click on Language selection option
   And select the mandatory language

  @smoke @BiometricDeviceNotDetected
  Scenario: Log in with Biometrics - Device Not Detected (TC_04)
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Biometrics
   Then verify secure biometric interface is displayed
   And verify uin vid option is displayed on biometric screen
   When user clicks on uin vid option on biometric screen
   Then verify vid text field is displayed on biometric screen
   When user enters prerequisite uin into biometric vid field
   And user clicks continue on biometric login id screen
   Then verify scanning devices message is displayed on biometric screen
   Then verify device not found message is displayed on biometric screen
   When user clicks on biometric device scan retry button
   Then verify scanning devices message is displayed on biometric screen
   Then verify biometric device scan completed with no device found

  @smoke @BiometricDeviceDetectedOnRetry
  Scenario: Log in with Biometrics - Device discovered after Mock MDS retry (TC_06)
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Biometrics
   Then verify secure biometric interface is displayed
   And verify uin vid option is displayed on biometric screen
   When user clicks on uin vid option on biometric screen
   Then verify vid text field is displayed on biometric screen
   When user enters prerequisite uin into biometric vid field
   And user clicks continue on biometric login id screen
   Then verify scanning devices message is displayed on biometric screen
   Then verify device not found message is displayed on biometric screen
   When mock mds is started for biometric device scan
   And user clicks on biometric device scan retry button
   Then verify scanning devices message is displayed on biometric screen
   And verify device not found message is cleared after mock mds retry scan
   And verify biometric device is discovered on biometric screen
   Then verify biometric device scan completed with device discovered

  @smoke @NeedsUIN @BiometricLogin
  Scenario: Log in with Biometrics - Mock MDS fingerprint login (TC_05)
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Biometrics
   Then verify secure biometric interface is displayed
   And verify uin vid option is displayed on biometric screen
   When user clicks on uin vid option on biometric screen
   Then verify vid text field is displayed on biometric screen
   When user enters prerequisite uin into biometric vid field
   And user clicks continue on biometric login id screen
   When mock mds is started for biometric device scan
   Then verify biometric device is discovered on biometric screen
   When user clicks biometric scan and verify button
   Then verify user is authenticated via biometrics successfully
   And verify biometric login completed to attention screen or relying party

  @smoke @PasswordLogin @MOSIP-44877
  Scenario Outline: IdP-UI Login with Password (positive and negative flows) - <test_data_id>
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And clicks on login with password button in login screen page
   Then verify mobile number option is present for authentication
   Then verify vid option is present for authentication
   Then verify email option is present for authentication
   Then verify nrc id option is present for authentication
   Then verify password field is present for authentication
   When user selects uin login id type if available
   When user enters prerequisite uin into password login id field
   And user enters "WrongPass@123" into password field
   And click on password login button
   Then verify user should get invalid credentials error message in authentication screen
   When user enters prerequisite uin into password login id field
   And user enters the correct password
   And click on password login button
   Then verify user navigate to Attention screen
   And clicks on proceed button in attention page

  Examples:
   | test_data_id    |
   | TC_Pwd_Login_01 |

  @smoke @PasswordLogin
  Scenario Outline: Login with Password using configured identities - <test_data_id>
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And clicks on login with password button in login screen page
   Then verify mobile number option is present for authentication
   Then verify vid option is present for authentication
   Then verify email option is present for authentication
   Then verify nrc id option is present for authentication
   Then verify password field is present for authentication
   When user selects "<login id type>" login id type for password authentication
   And user enters configured password login id for "<identity key>"
   And user enters the correct password
   And click on password login button
   Then verify user navigate to Attention screen
   And clicks on proceed button in attention page

  Examples:
   | test_data_id         | login id type | identity key      |
   | TC_Pwd_Login_mockUin | uin           | mockUin           |
   | TC_Pwd_Login_uin     | uin           | passwordLoginUin  |
   | TC_Pwd_Login_email   | email         | emailLoginId      |

  @smoke @PasswordLogin @supportOfPrefixAndPostfix
  Scenario: Verifying invalid mobile, VID and email input validation on the password login screen
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And clicks on login with password button in login screen page
   Then verify mobile number option is present for authentication
   Then verify nrc id option is present for authentication
   Then verify vid option is present for authentication
   Then verify email option is present for authentication
   Then verify password field is present for authentication
   Then verify mobile number selected for authentication
   And clicks on prefix number button in authentication screen page
   Then verify khm country prefix displayed for mobile number
   Then verify ind country prefix displayed for mobile number
   And clicks on prefix number button in authentication screen page
   Then user enters invalid mobile number into the mobile number field
   And user enters the correct password
   And click on password login button
   Then verify user should get invalid individual id error message in authentication screen
   Then clicks on vid option button in authentication screen page
   When user enters invalid vid into vid field
   And user enters the correct password
   And click on password login button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into vid field
   And click on password login button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into vid field
   Then clicks on email option button in authentication screen page
   When user enters invalid email into email field
   And user enters the correct password
   And click on password login button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into email field
   And click on password login button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into email field

  @smoke @PasswordLogin
  Scenario: Password login then second factor OTP if prompted
   Given user captures the authorize url
   Then verify login title and subtitle are displayed
   When click on Language selection option
   And select the mandatory language
   And clicks on login with password button in login screen page
   When user selects uin login id type if available
   When user enters prerequisite uin into password login id field
   And user enters the correct password
   And click on password login button
   When user completes second factor otp if prompted
   Then verify user navigate to Attention screen or user profile
