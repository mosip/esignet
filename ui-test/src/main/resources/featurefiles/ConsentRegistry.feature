#@smokeAndRegression
Feature: ES-3 Consent Registry
  Automates Consent Registry test cases TC_02, TC_07, TC_08, TC_11, and TC_12.
  Consent storage/lookup works under mock plugin too on this Thunder/esignet-go deployment - confirmed
  live. TC_07 uses oidcClientId=primary,secondary when set; otherwise it reuses the existing primary
  oidcClientId. The DB-assertion steps (TC_02/TC_12) self-skip when esignetDbHost/esignetDbPassword
  (or database-host/postgres-password) in config.properties isn't configured.

  @smoke @ConsentRegistry @TC02
  Scenario: TC_02 Verify stored consent in Consent table via OTP login
    Given user captures the authorize url
    Then verify login title and subtitle are displayed
    When click on Language selection option
    And select the mandatory language
    And user click on Login with Otp
    Then user enters Registered mobile number into the mobile number field
    And user click on get otp button
    When user enters the correct otp
    And click on verify Otp button
    Then user completes consent flow through eKYC and returns to relying party
    Then verify consent is stored in consent table with psu token and json consent

  @smoke @ConsentRegistry @TC07 @TC08
  Scenario: TC_07 Verify consent is requested when same UIN logs into a second client for the same partner
    Given user captures the authorize url
    Then verify login title and subtitle are displayed
    When click on Language selection option
    And select the mandatory language
    And user click on Login with Otp
    Then user enters Registered mobile number into the mobile number field
    And user click on get otp button
    When user enters the correct otp
    And click on verify Otp button
    Then user completes consent flow through eKYC and returns to relying party
    When user relaunches esignet authorize url for secondary portal
    When click on Language selection option
    And select the mandatory language
    When user authenticates with otp when login screen is displayed
    Then user reaches consent screen through eKYC after authentication

  @smoke @ConsentRegistry @TC11
  Scenario: TC_11 Consent not asked when same VID already consented via eSignet OTP
    Given user captures the authorize url
    Then verify login title and subtitle are displayed
    When click on Language selection option
    And select the mandatory language
    And user click on Login with Otp
    Then user enters Registered mobile number into the mobile number field
    And user click on get otp button
    When user enters the correct otp
    And click on verify Otp button
    Then user completes consent flow through eKYC and returns to relying party
    When user relaunches esignet authorize url without consent prompt
    When click on Language selection option
    And select the mandatory language
    And user click on Login with Otp
    Then user enters Registered mobile number into the mobile number field
    And user click on get otp button
    When user enters the correct otp
    And click on verify Otp button
    Then verify consent is not requested after authentication

  @smoke @ConsentRegistry @TC12
  Scenario: TC_12 Verify empty accepted_claims when optional claims are declined
    Given user captures the authorize url
    Then verify login title and subtitle are displayed
    When click on Language selection option
    And select the mandatory language
    And user click on Login with Otp
    Then user enters Registered mobile number into the mobile number field
    And user click on get otp button
    When user enters the correct otp
    And click on verify Otp button
    When user completes consent registry flow declining optional claims
    Then verify user is navigated to user profile page
    Then verify consent table has empty accepted claims for current client
