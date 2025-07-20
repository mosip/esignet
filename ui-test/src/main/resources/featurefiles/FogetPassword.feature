#@smokeAndRegression
Feature: Esignet Login Options Page
  This feature file is for verifying the forget password options page

  @smoke @forgetPasswordOptionsVerification
  Scenario Outline: Verify the forget password options for phonenumber
    Given click on Sign In with eSignet
    Then validate that the logo is displayed
    Then user click on Login with password
    Then user verify forget password link
    Then user click on forget password link
    Then user verify browser redirected to reset-password
    Then user verify country code prefix
    Then user verify the water mark text inside phonenumber
    Then user verify country code is not editable
    Then user verify forget password heading
    Then user verify back button on forget password
    Then user verify subheading on forget password
    Then user verify username label on forget password
    Then user verify fullname label on forget password
    Then user verify continue button on forget password
    Then user verify footer on forget password
    When user enters "<number with starting 0>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "invalid"
    When user enters "<number with all zeros>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "invalid"
    When user enters "<8 digit number start with 0>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "invalid"
    When user enters "<alphanumeric input>" into the mobile number field
    Then mobile number input should remain empty
    When user enters "<special char input>" into the mobile number field
    Then mobile number input should remain empty
    When user enters "<9 digit number>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "valid"
    When user enters "<8 digit number>" into the mobile number field
    And user clicks outside the input to trigger validation
    Then phone number should be "valid"
    Then user verify continue button is not enabled

    Examples:
      | 9 digit number | 8 digit number | number with starting 0 | number with all zeros | 8 digit number start with 0 | alphanumeric input | special char input |
      | 240578296      | 12345678       | 012345678              | 000000000             | 01234567                    | abcdef             | @#$%               |

@smoke @forgetPasswordFullNameVerification
Scenario Outline: Verify the forget‑password options for full name
  Given click on Sign In with eSignet
  Then user click on Login with password
  And user click on forget password link
  When user enters "<Numeric>" into the fullname field
  And user clicks outside the input to trigger validation
  Then user verify full name error message
  When user enters "<Alphanumeric>" into the fullname field
  And user clicks outside the input to trigger validation
  Then user verify full name error message
  When user enters "<English>" into the fullname field
  And user clicks outside the input to trigger validation
  Then user verify full name error message
  When user enters "<LongKhmer>" into the fullname field
  And user clicks outside the input to trigger validation
  Then user verify full name error message not displayed  
  And only 30 characters are retained in the fullname field
  When user enters "<ValidKhmer>" into the fullname field
  And user clicks outside the input to trigger validation
  Then user verify full name error message not displayed  
  Then user verify continue button is not enabled
  When user enters "<8 digit number>" into the mobile number field
  Then user verify continue button is enabled  
  

Examples:
  | Numeric   | Alphanumeric | English    | LongKhmer | ValidKhmer |
  | 240578296 | abc123       | myname   | សុខ សេរី សាន សុវណ្ណ សុភ័ក្រ សាន សុផល សុជាតា សុវណ្ណ | សុខសេរី  |
