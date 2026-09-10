Feature: Esignet KBI Login Form
  End-to-end Knowledge-Based Identity login for the go-sunbird deployment.
  Three scenarios / three Chrome sessions. Refresh is isolated so a 401
  recovery failure does not fail the main login→consent→RP path.

  @smoke @kbi @KbiForm @kbiSchemaFetch @ES-2058
  Scenario: KBI form rendering language and negative credential checks
   Given user captures the authorize url
   Then verify the KBI schema fetch request required no authentication
   When user clicks on Login with KBI
   Then verify KBI form is displayed
   Then verify KBI form shows Policy Number Fullname and Date of Birth fields
   Then verify KBI page content is displayed in default English language
   Then verify KBI login button text is displayed in default English language
   Then verify KBI login button is disabled
   And user clicks on the KBI login button
   Then verify KBI form shows validation errors for empty mandatory fields
   And user fills all mandatory fields in the KBI form
   Then verify KBI login button is enabled
   And user fills invalid KBI credentials
   And user clicks on the KBI login button
   Then verify KBI authentication is not successful
   When click on Language selection option
   And select the mandatory language
   Then verify KBI form shows Policy Number Fullname and Date of Birth fields

  @smoke @kbi @KbiForm @leaveSitePrompt @ES-2058
  Scenario: KBI form browser refresh recovers with a fresh authorize session
   Given user captures the authorize url
   When user clicks on Login with KBI
   Then verify KBI form is displayed
   And user fills all mandatory fields in the KBI form
   And user refreshes the browser and a leave site prompt should appear
   And user cancels the leave site prompt
   Then verify KBI form is displayed
   And user fills all mandatory fields in the KBI form
   And user clicks on the KBI login button
   Then verify KBI authentication is successful

  @smoke @kbi @KbiForm @ES-2058
  Scenario: Complete KBI login through consent and return to relying party
   Given user captures the authorize url
   When user clicks on Login with KBI
   Then verify KBI form is displayed
   And user fills all mandatory fields in the KBI form
   And user clicks on the KBI login button
   Then verify KBI authentication is successful
   And user completes consent flow through eKYC and returns to relying party
   Then verify user is returned to the relying party after KBI login
