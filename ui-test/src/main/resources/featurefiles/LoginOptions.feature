#@smokeAndRegression
Feature: Esignet Login Options Page
  This feature file is for verifying the Login options page

  @smoke @AuthenticaionPage
  Scenario Outline: Verifying Login page Options
   Given user captures the authorize url
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
   | हिंदी        | लॉगिन    |
   
  @mobile @mobileViewFeatures
  Scenario: Verifying the UI in mobile view
   When user triggers the authorization endpoint, the response should have status code 200 and contain valid HTML with JS content
   Given user captures the authorize url
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
   
   
   @smoke @supportOfPrefixAndPostfix
  Scenario: Verifying support for multiple prefix and postfix type for the individual ID
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
   Then verify get otp button is disabled in authentication screen
   Then user enters Registered mobile number into the mobile number field
   Then verify get otp button is enabled in authentication screen
   And user click on get otp button
   Then verify user navigate to verify otp screen
   Then verify the otp verification button is disabled on the verification screen
   When user enters the correct otp
   Then verify the otp verification button is enabled on the verification screen
   And click on verify Otp button
   Then verify user navigate to Attention screen

  @smoke @supportOfPrefixAndPostfix
  Scenario: Verifying invalid VID and email input validation on the OTP login screen
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
   Then verify get otp button is disabled in authentication screen
   Then clicks on email option button in authentication screen page
   When user enters invalid email into email field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into email field
   And user click on get otp button
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into email field
   Then verify get otp button is disabled in authentication screen

  @smoke @supportOfPrefixAndPostfix
  Scenario: Verifying invalid mobile, VID and email input validation on the password login screen
   When click on Language selection option
   And select the mandatory language
   And clicks on login with password button in login screen page
   Then verify mobile number option is present for authentication
   Then verify nrc id option is present for authentication
   Then verify vid option is present for authentication
   Then verify email option is present for authentication
   Then verify mobile number selected for authentication
   And clicks on prefix number button in password authentication screen page
   Then verify khm country prefix displayed for mobile number
   Then verify ind country prefix displayed for mobile number
   And clicks on prefix number button in password authentication screen page
   Then user enters invalid mobile number into the mobile number field
   Then user enters valid password into mobile password field
   And clicks on login button with password in authentication screen page
   Then verify user should get invalid individual id error message in authentication screen
   Then clicks on vid option button in authentication screen page
   When user enters invalid vid into vid field in password authentication screen page
   Then user enters valid password into vid password field
   And clicks on login button with password in authentication screen page
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into vid field in password authentication screen page
   And clicks on login button with password in authentication screen page
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into vid field in password authentication screen page
   Then verify password with login button is disabled in authentication screen
   Then clicks on email option button in authentication screen page
   When user enters invalid email into email field
   Then user enters valid password into email password field
   And clicks on login button with password in authentication screen page
   Then verify user should get invalid individual id error message in authentication screen
   When user enters special characters into email field
   And clicks on login button with password in authentication screen page
   Then verify user should get invalid individual id error message in authentication screen
   When user enters only space into email field
   Then verify password with login button is disabled in authentication screen

  @smoke @VID @ConsentRegistryVID
  Scenario: Verifying consent flow through eKYC for VID login (requires mosipid plugin and prerequisite VIDs)
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
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
   When user enters prerequisite vid2 into vid field
   And user click on get otp button
   When user enters the correct otp
   And click on verify Otp button
   Then verify consent is not requested after authentication

     @smoke @BiometricDeviceNotDetected @MOSIP-22718
     Scenario: Log in with Biometrics - Device Not Detected (MOSIP-22718 TC_04)
      Given user captures the authorize url
      When click on Language selection option
      And select the mandatory language
      And user click on Login with Biometrics
      Then verify secure biometric interface is displayed
      And verify uin vid option is displayed on biometric screen
      When user clicks on uin vid option on biometric screen
      Then verify vid text field is displayed on biometric screen
      And verify scanning devices message is displayed on biometric screen
      And verify retry scan button is not displayed while scanning devices
      Then verify device not found message is displayed on biometric screen
      When user clicks on biometric device scan retry button
      Then verify scanning devices message is displayed on biometric screen

@smoke @AuthenticaionPage @supportOfPrefixAndPostfix
  Scenario: TC_51,TC_61,TC_49 - Verify the postfix for mobile/email fields and maxLength precedence for mobile number
   Given user captures the authorize url
   When click on Language selection option
   And select the mandatory language
   And clicks on login with password button in login screen page
   And user clicks on mobile number option
   Then verify the postfix for mobile number option is "@phone"
   And user clicks on mobile prefix dropdown button in password login screen
   And clicks on ind country code prefix in authentication screen page
   Then verify the maxLength precedence is honored for mobile number with "IND" prefix
   And clicks on email option button in authentication screen page
   Then verify the postfix for "email" option is "@email"

  @smoke @AuthenticaionPage @UpdatedTitleAndSubTitle @ES-216
  Scenario: TC_07 - Verify title and subtitle are displayed as per updated text given while updating client details
   Given user creates the client with updated title and subtitle values
   And user captures the authorize url
   Then verify title and subtitle should be displayed as per updated client details

  @smoke @AuthenticaionPage @TitleOnlyPurposeLogin @ES-216
  Scenario: TC_08a - Verify default login subtitle is displayed when client is created with purpose type login, a title but no subtitle
   Given user creates the client with a title but no subtitle for purpose type "login"
   And user captures the authorize url
   Then verify default subtitle "is requesting authentication for login" should be displayed when subtitle is not configured

  @smoke @AuthenticaionPage @TitleOnlyPurposeVerify @ES-216
  Scenario: TC_08b - Verify default verify subtitle is displayed when client is created with purpose type verify, a title but no subtitle
   Given user creates the client with a title but no subtitle for purpose type "verify"
   And user captures the authorize url
   Then verify default subtitle "is requesting authentication for verification" should be displayed when subtitle is not configured

  @smoke @AuthenticaionPage @TitleOnlyPurposeLink @ES-216
  Scenario: TC_08c - Verify default link subtitle is displayed when client is created with purpose type link, a title but no subtitle
   Given user creates the client with a title but no subtitle for purpose type "link"
   And user captures the authorize url
   Then verify default subtitle "is requesting authentication to link your account" should be displayed when subtitle is not configured

  @smoke @AuthenticaionPage @NoTitleAndSubTitle @ES-216
  Scenario: TC_10 - Verify default title and subtitle are displayed when client is created with a purpose type but without title and without subtitle
   Given user creates the client with null title and subtitle values
   And user captures the authorize url
   Then verify default title and subtitle should be displayed when both title and subtitle are not configured

  @smoke @AuthenticaionPage @SubtitleOnlyPurposeLogin @ES-216
  Scenario: TC_11a - Verify default login title is displayed when purpose type is valid and title is null (subtitle configured, purpose type login)
   Given user creates the client with a subtitle but no title for purpose type "login"
   And user captures the authorize url
   Then verify default title "Login using eSignet" should be displayed when title is not configured

  @smoke @AuthenticaionPage @SubtitleOnlyPurposeVerify @ES-216
  Scenario: TC_11b - Verify default verify title is displayed when purpose type is valid and title is null (subtitle configured, purpose type verify)
   Given user creates the client with a subtitle but no title for purpose type "verify"
   And user captures the authorize url
   Then verify default title "Verify using eSignet" should be displayed when title is not configured

  @smoke @AuthenticaionPage @SubtitleOnlyPurposeLink @ES-216
  Scenario: TC_11c - Verify default link title is displayed when purpose type is valid and title is null (subtitle configured, purpose type link)
   Given user creates the client with a subtitle but no title for purpose type "link"
   And user captures the authorize url
   Then verify default title "Link using eSignet" should be displayed when title is not configured

  @smoke @AuthenticaionPage @NoPurpose @ES-216
  Scenario: TC_20 - Verify preferred-ID-to-login text is displayed when client is created with an empty purpose type and has more than one login ID option
   Given user creates the client with an empty purpose type
   And user captures the authorize url
   And user click on Login with Otp
   Then verify select preferred ID text based on purpose type when more than one auth factor is present

  @smoke @AuthenticaionPage @NetworkError
  Scenario: TC_70,TC_71 - Verify network error handling and recovery once network is back
   Given user captures the authorize url
   When click on Language selection option
   And select the mandatory language
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   When user's internet connection is disconnected
   Then verify the network error screen is displayed
   And verify language dropdown is not displayed on the network error screen
   When user's internet connection is restored
   And user clicks on try again button on the network error screen
   And user click on Login with Otp
   Then user enters Registered mobile number into the mobile number field
   And user click on get otp button
   Then verify user navigate to verify otp screen