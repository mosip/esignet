#@smokeAndRegression
Feature: Esignet KBI Login Form
  This feature file is for verifying the Knowledge-Based Identity (KBI) login form.
  Covers only the test cases from ES-2058 that don't depend on the exact schema-driven
  field rendering (which is produced at runtime by an external package not vendored
  in this repo) - schema-content-dependent cases (field types, regex validation,
  mandatory-flag/asterisk rendering, per-field inline errors, multi-language labels,
  and schema-reconfiguration cases) are handled separately.

  # KBI is not offered by this environment's default client/policy; skip until the
  # client/policy negotiates the KBI auth factor.
  # @smoke @kbi @KbiForm @kbiSchemaFetch @leaveSitePrompt @ES-2058
  # Scenario: TC_01,08,09,10,18,19,22,23 - Verify KBI schema fetch, default language, button state, reload behavior and authentication
  #   Then verify the KBI schema fetch request required no authentication
  #   When user clicks on Login with KBI
  #   Then verify KBI form is displayed
  #   Then verify KBI page content is displayed in default English language
  #   Then verify KBI login button text is displayed in default English language
  #
  #   When click on Language selection option
  #   And select the mandatory language
  #   Then verify KBI login button is disabled
  #
  #   And user fills all mandatory fields in the KBI form
  #   Then verify KBI login button is enabled
  #
  #   And user refreshes the browser and a leave site prompt should appear
  #   And user cancels the leave site prompt
  #   Then verify KBI form is displayed
  #
  #   And user refreshes the browser and a leave site prompt should appear
  #   And user confirms the leave site prompt
  #   Then verify KBI form is displayed
  #   Then verify KBI form fields are empty
  #
  #   And user fills all mandatory fields in the KBI form
  #   Then verify KBI login button is enabled
  #   And user clicks on the KBI login button
  #   Then verify KBI authentication is successful
