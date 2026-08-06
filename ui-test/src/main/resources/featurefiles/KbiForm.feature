Feature: eSignet KBI login form
  Verifies the KBI (Knowledge-Based Identity) login form renders in line with the latest
  server-side schema (mosip.esignet.authenticator.default.auth-factor.kbi.field-details).

  @smoke @kbi
  Scenario: Verify that the KBI form UI loads based on the latest schema
    When user clicks on login with KBI
    Then KBI form fields and labels should be aligned to the latest schema

  @smoke @kbi
  Scenario: Validate the field based on the regex passed in the schema
    When user clicks on login with KBI
    Then KBI field should show validation error for input not matching the schema regex

  @smoke @kbi
  Scenario: Verify that fields in KBI form are mandated based on the mandatory flag in schema
    When user clicks on login with KBI
    Then KBI fields should be marked mandatory or optional according to the schema

  @smoke @kbi
  Scenario: Verify the asterisk symbol for mandatory fields
    When user clicks on login with KBI
    Then KBI mandatory fields should show asterisk symbol according to the schema

  @smoke @kbi
  Scenario: Verify the inline error message when mandatory fields are not filled
    When user clicks on login with KBI
    Then KBI form should show inline error message for empty mandatory fields

  # Verifies each field the schema declares renders as one of the UI-schema-supported input types
  # (Text, Email, Number, Checkbox, Radio, Dropdown, Date) and reports which were exercised. Covering
  # all 7 in one run needs the server-side kbi.field-details schema to declare a field of each type.
  @smoke @kbi
  Scenario: Verify the supported input field types in the KBI UI schema
    When user clicks on login with KBI
    Then KBI form should support the input field types defined in the schema

  # The KBI form renders in the run language (ui_locales on the authorize URL). Runner re-runs each
  # scenario per run language (runLanguage, else the app's supported languages), covering each one.
  # Dropdown/checkbox checks skip unless the server schema declares a field of that type.
  @smoke @kbi
  Scenario: Verify field labels are displayed in the selected language
    When user clicks on login with KBI
    Then KBI field labels should be displayed in the selected language

  @smoke @kbi
  Scenario: Verify dropdown options are displayed in the selected language
    When user clicks on login with KBI
    Then KBI dropdown options should be displayed in the selected language

  @smoke @kbi
  Scenario: Verify checkbox labels are displayed in the selected language
    When user clicks on login with KBI
    Then KBI checkbox labels should be displayed in the selected language

  # Fallback only observable when the run language is non-English and the schema omits it for a field.
  @smoke @kbi
  Scenario: Verify fallback to English when the schema does not specify the selected language
    When user clicks on login with KBI
    Then KBI field labels should fall back to English when the schema lacks the selected language

  # Simulates a mid-flow network drop via the browser's offline emulation (local Chromium only).
  @smoke @kbi
  Scenario: Verify the KBI form handles a network disconnect
    When user clicks on login with KBI
    Then KBI form should show an error and reload the schema on network disconnect

  # The next 4 scenarios share one fully schema-driven step: it fills whatever fields the current
  # transaction's KBI schema declares as required with matching Sunbird RC policy data (leaving
  # optional fields blank) and asserts the login reaches the consent screen. The same code covers
  # all 4 cases unmodified - what differs between them is the server-side kbi.field-details schema
  # itself, which must already be reconfigured to the intended variant (default/added field/removed
  # field/single field) before that scenario is run; this suite cannot change server config.
  # Each carries its own tag so a run can select just the one matching the deployed schema, e.g.
  # -Dcucumber.filter.tags="@KbiSingleField" - otherwise one pass reports 4 passes for 1 schema.

  @smoke @kbi @KbiOptionalFields
  Scenario: Verify that fields in KBI form are not mandated when mandatory flag in schema is set to false
    When user clicks on login with KBI
    Then KBI authentication should be successful

  @smoke @kbi @KbiAddedFields
  Scenario: Authenticate by adding 1 or more fields to the existing KBI form
    When user clicks on login with KBI
    Then KBI authentication should be successful

  @smoke @kbi @KbiRemovedFields
  Scenario: Authenticate by removing existing field from the KBI form
    When user clicks on login with KBI
    Then KBI authentication should be successful

  @smoke @kbi @KbiSingleField
  Scenario: Authenticate when only one field is configured in KBI form
    When user clicks on login with KBI
    Then KBI authentication should be successful
