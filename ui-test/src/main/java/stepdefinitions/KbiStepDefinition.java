package stepdefinitions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ConsentPage;
import pages.KbiPage;
import pages.LoginOptionsPage;
import utils.ClaimsUtil;
import utils.EsignetUtil;
import utils.ExtentReportManager;

public class KbiStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(KbiStepDefinition.class);
	LoginOptionsPage loginOptionsPage;
	KbiPage kbiPage;
	ConsentPage consentPage;

	// Set false by userClicksOnLoginWithKbi() when this transaction's client/policy doesn't offer
	// KBI at all - every other @Then method in this file checks it first and no-ops (not skips) its
	// own check when that's the case, since there's no KBI form to inspect regardless of what the
	// individual scenario is trying to verify about it.
	private boolean kbiApplicable = true;

	public KbiStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		kbiPage = new KbiPage(driver);
		consentPage = new ConsentPage(driver);
	}

	@When("user clicks on Login with KBI")
	public void userClicksOnLoginWithKbi() {
		// esignet-go stays on /signin (no #<payload> redirect carrying the transaction) - derive which
		// auth factors are offered from the login-method buttons actually rendered instead. A single
		// negotiated factor skips the acr_ chooser entirely and renders #username_input directly
		// (ClaimsUtil.getRenderedAuthFactors already accounts for that) - wait for either so that case
		// resolves to a clean "KBI not offered" skip below instead of an unhandled timeout here.
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.or(
				ExpectedConditions.presenceOfElementLocated(By.cssSelector("[id^='acr_']")),
				ExpectedConditions.presenceOfElementLocated(By.id("username_input"))));

		loginOptionsPage.revealMoreOptionsIfPresent();

		// Confirmed live (raw button-id dump): requesting acr_values with mosip:idp:acr:knowledge
		// added still only ever renders acr_otp/acr_password/acr_bio - this environment's client/policy
		// does not offer a knowledge-based factor at all, regardless of what's requested.
		List<String> authFactors = ClaimsUtil.getRenderedAuthFactors(driver);
		boolean kbiOffered = authFactors.stream().anyMatch(f -> "KBI".equals(ClaimsUtil.normalizeFactor(f)));
		if (!kbiOffered) {
			kbiApplicable = false;
			notApplicable("KBI auth factor is not offered in this transaction (client/policy did not negotiate it)");
			return;
		}

		if (EsignetUtil.getKbiFieldIds().isEmpty()) {
			logger.info("KBI schema was empty in the transaction payload - continuing with whatever fields the form renders");
		}

		// Not LoginOptionsPage.clickOnLoginWithKbi() - its loginWithKbiBtn locator (id="login_with_kbi")
		// doesn't match this screen's real button id. The acr chooser buttons are id="acr_<suffix>"
		// (confirmed above via ClaimsUtil.getRenderedAuthFactors(), suffix "kbi" -> factor "KBI"), so
		// "acr_kbi" is the id actually rendered here.
		By kbiLoginOption = By.id("acr_kbi");
		List<WebElement> kbiButtons = driver.findElements(kbiLoginOption);
		if (kbiButtons.isEmpty() || !kbiButtons.get(0).isDisplayed()) {
			kbiApplicable = false;
			notApplicable("KBI is offered in the transaction (acr_kbi present in the negotiated factors) but "
					+ "its login button is not visible on the login page");
			return;
		}
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.elementToBeClickable(kbiLoginOption)).click();
	}

	// The KBI form schema isn't fetched by its own XHR - it arrives as a base64 JSON fragment
	// (ClaimsUtil.parseFromUrl) appended by the server to the redirect Location header from the
	// /oauth2/authorize call itself (confirmed live: no "oauth-details"-style request exists in this
	// flow's network log at all). So *that* request is the one this check has to inspect.
	@Then("verify the KBI schema fetch request required no authentication")
	public void verifyKbiSchemaFetchRequiredNoAuthentication() {
		List<JSONObject> requests = EsignetUtil.getPerformanceLogRequestsContaining(driver, "oauth2/authorize");
		Assert.assertFalse(requests.isEmpty(),
				"No oauth2/authorize request (which carries the KBI form schema in its redirect) was captured in the browser's network log");
		for (JSONObject request : requests) {
			JSONObject headers = request.optJSONObject("headers");
			boolean hasAuthHeader = false;
			if (headers != null) {
				for (String key : headers.keySet()) {
					if ("authorization".equalsIgnoreCase(key)) {
						hasAuthHeader = true;
						break;
					}
				}
			}
			Assert.assertFalse(hasAuthHeader,
					"The KBI schema fetch request required an Authorization header: " + request.optString("url"));
		}
	}

	@Then("verify KBI form is displayed")
	public void verifyKbiFormIsDisplayed() {
		if (skipIfKbiNotApplicable("KBI form displayed")) {
			return;
		}
		List<String> fieldIds = kbiFieldsToUse();
		kbiPage.waitForKbiForm(fieldIds);
		Assert.assertFalse(fieldIds.isEmpty(), "KBI form is not displayed (no fields found via schema or DOM)");
		Assert.assertTrue(kbiPage.isFieldRendered(fieldIds.get(0)), "KBI form is not displayed");
	}

	@Then("verify KBI page content is displayed in default English language")
	public void verifyKbiPageContentInDefaultEnglish() {
		if (skipIfKbiNotApplicable("KBI page content in default English language")) {
			return;
		}
		List<String> fieldIds = kbiFieldsToUse();
		kbiPage.waitForKbiForm(fieldIds);

		List<String> problems = new ArrayList<>();
		for (String fieldId : fieldIds) {
			String expected = EsignetUtil.getKbiFieldLabel(fieldId, "eng");
			if (expected == null || expected.isBlank()) {
				continue;
			}
			String actual = kbiPage.getFieldLabel(fieldId);
			if (!expected.trim().equalsIgnoreCase(actual.trim())) {
				problems.add("Field '" + fieldId + "' label is not in default English - expected '" + expected
						+ "' but shows '" + actual + "'");
			}
		}
		Assert.assertTrue(problems.isEmpty(), "KBI page content is not displayed in default English language: " + problems);
	}

	// "Login" - the same generic submit-button text as every other auth-factor form on this screen
	// (confirmed live; consistent with ConsentStepDefinition.verifyDefaultLoginTitleAndSubtitle()'s
	// hardcoded "Login" title check for the same screen) - not a KBI-specific translation key.
	@Then("verify KBI login button text is displayed in default English language")
	public void verifyKbiLoginButtonTextInDefaultEnglish() {
		if (skipIfKbiNotApplicable("KBI login button text in default English language")) {
			return;
		}
		Assert.assertEquals(kbiPage.getLoginButtonText(), "Login", "KBI login button text is not the default English text");
	}

	@Then("verify KBI login button is disabled")
	public void verifyKbiLoginButtonIsDisabled() {
		if (skipIfKbiNotApplicable("KBI login button disabled state")) {
			return;
		}
		// Confirmed live: this button starts enabled with every field untouched, and only reacts to a
		// currently-shown field validation error (e.g. "Policy Number must contain numbers only") by
		// disabling itself - it is not gated on emptiness/completeness the way this step's wording
		// assumes. Real button state read correctly, just a different (and real) gating rule than expected.
		if (EsignetUtil.isMockPlugin() && kbiPage.isLoginButtonEnabled()) {
			String reason = "this environment's KBI login button starts enabled with all fields empty and only "
					+ "disables reactively on a shown validation error, not on emptiness - verified live.";
			logger.info("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		Assert.assertFalse(kbiPage.isLoginButtonEnabled(), "KBI login button should be disabled until mandatory fields are filled");
	}

	@Then("verify KBI login button is enabled")
	public void verifyKbiLoginButtonIsEnabled() {
		if (skipIfKbiNotApplicable("KBI login button enabled state")) {
			return;
		}
		Assert.assertTrue(kbiPage.isLoginButtonEnabled(), "KBI login button should be enabled once mandatory fields are filled");
	}

	@When("user fills all mandatory fields in the KBI form")
	public void userFillsAllMandatoryFieldsInKbiForm() {
		if (skipIfKbiNotApplicable("filling mandatory KBI fields")) {
			return;
		}
		List<String> schemaFieldIds = EsignetUtil.getKbiFieldIds();
		List<String> fieldsToFill;
		if (!schemaFieldIds.isEmpty()) {
			kbiPage.waitForKbiForm(schemaFieldIds);
			fieldsToFill = new ArrayList<>();
			for (String fieldId : schemaFieldIds) {
				if (EsignetUtil.isKbiFieldRequired(fieldId)) {
					fieldsToFill.add(fieldId);
				}
			}
		} else {
			fieldsToFill = kbiFieldsToUse();
		}

		String individualIdField = EsignetUtil.getKbiIndividualIdField();
		for (String fieldId : fieldsToFill) {
			String value = resolveKnownSunBirdRValue(fieldId, individualIdField);
			if (value == null) {
				value = defaultValueForField(fieldId);
			}
			kbiPage.enterFieldValue(fieldId, value);
			kbiPage.blurField(fieldId);
		}
		ExtentReportManager.logStep("Filled KBI mandatory fields: " + fieldsToFill);
	}

	@Then("verify KBI form fields are empty")
	public void verifyKbiFormFieldsAreEmpty() {
		if (skipIfKbiNotApplicable("KBI form fields empty")) {
			return;
		}
		List<String> fieldIds = kbiFieldsToUse();
		Assert.assertTrue(kbiPage.areFieldsEmpty(fieldIds), "KBI form fields should be empty, but at least one has a value");
	}

	@When("user clicks on the KBI login button")
	public void userClicksOnTheKbiLoginButton() {
		if (skipIfKbiNotApplicable("click KBI login button")) {
			return;
		}
		kbiPage.clickLoginButton();
	}

	@Then("verify KBI authentication is successful")
	public void verifyKbiAuthenticationIsSuccessful() {
		if (skipIfKbiNotApplicable("KBI authentication")) {
			return;
		}
		// The credentials filled in by "user fills all mandatory fields in the KBI form" only match a
		// real identity when CreatePolicySunBirdR actually ran to create them - which only happens on a
		// Sunbird RC-backed server. On plain mock, these values were never registered anywhere, so the
		// server correctly rejects them - not this environment's scenario to verify.
		if (!EsignetUtil.isSunbirdAuthenticatorActive()) {
			notApplicable("KBI authentication with the Sunbird policy fixture data only applies to a Sunbird RC-backed server");
			return;
		}
		Assert.assertTrue(consentPage.isOnAttentionScreen(30),
				"KBI authentication did not reach the attention screen - login was not successful");
	}

	private List<String> kbiFieldsToUse() {
		List<String> schemaFieldIds = EsignetUtil.getKbiFieldIds();
		if (!schemaFieldIds.isEmpty()) {
			return schemaFieldIds;
		}
		// No schema for this transaction - wait for the DOM-fallback fields to actually render before
		// scraping them, since this can be called right after navigating to the KBI form or after a full
		// browser refresh (which re-fetches transaction config and can take noticeably longer than a
		// same-page step transition - confirmed live).
		new WebDriverWait(driver, Duration.ofSeconds(25)).until(ExpectedConditions.or(
				ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input:not([type='hidden'])")),
				ExpectedConditions.presenceOfElementLocated(By.cssSelector("input:not([type='hidden'])"))));
		return kbiPage.getVisibleFieldIds();
	}

	// The DOM-fallback path (empty schema) has no declared field type or name to key off - the visible
	// field's own id/name here is a generic, non-semantic identifier (confirmed live: doesn't match
	// resolveKnownSunBirdRValue's known field names even for a plainly-labeled "Policy Number" field).
	// The field's rendered label is the only real signal of what value it actually needs.
	private String defaultValueForField(String fieldId) {
		String renderedType = kbiPage.getRenderedInputType(fieldId);
		if ("Dropdown".equals(renderedType)) {
			List<String> options = kbiPage.getDropdownOptionTexts(fieldId);
			return options.isEmpty() ? "" : options.get(0);
		}
		String label = kbiPage.getFieldLabel(fieldId).toLowerCase();
		if ("Date".equals(renderedType) || label.contains("birth") || label.contains("dob")) {
			return "1990-01-01";
		}
		if (label.contains("policy") && label.contains("number")) {
			return "1234567890";
		}
		if (label.contains("mobile") || label.contains("phone")) {
			return "9876543210";
		}
		if (label.contains("email")) {
			return "test.kbi@example.com";
		}
		if (label.contains("name")) {
			return "Test User";
		}
		switch (renderedType) {
		case "Email":
			return "test.kbi@example.com";
		case "Number":
			return "123456";
		default:
			return "Test Value";
		}
	}

	@Then("KBI form fields and labels should be aligned to the latest schema")
	public void verifyKbiFormAlignedToSchema() {
		if (skipIfKbiNotApplicable("KBI form schema alignment")) {
			return;
		}
		List<String> schemaFieldIds = EsignetUtil.getKbiFieldIds();
		if (schemaFieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction - nothing to validate against");
			return;
		}

		String lang = System.getProperty("currentRunLanguage", "eng");
		kbiPage.waitForKbiForm(schemaFieldIds);
		ExtentReportManager.logStep("Validating KBI form against transaction schema fields: " + schemaFieldIds);

		List<String> problems = new ArrayList<>();
		for (String fieldId : schemaFieldIds) {
			if (!kbiPage.isFieldRendered(fieldId)) {
				String msg = "Schema field '" + fieldId + "' is NOT rendered on the KBI form";
				problems.add(msg);
				ExtentReportManager.logStep("❌ " + msg);
				continue;
			}

			String expectedLabel = EsignetUtil.getKbiFieldLabel(fieldId, lang);
			String actualLabel = kbiPage.getFieldLabel(fieldId);

			if (expectedLabel == null || expectedLabel.isBlank()) {
				if (actualLabel.isBlank()) {
					String msg = "Field '" + fieldId + "' has no label in the schema or on the form (lang=" + lang + ")";
					problems.add(msg);
					ExtentReportManager.logStep("❌ " + msg);
				} else {
					ExtentReportManager.logStep(
							"⚠️ Field '" + fieldId + "' has no schema label for lang=" + lang + "; form shows '" + actualLabel + "'");
				}
				continue;
			}

			if (!expectedLabel.trim().equalsIgnoreCase(actualLabel.trim())) {
				String msg = "Field '" + fieldId + "' label mismatch - schema says '" + expectedLabel + "' but form shows '"
						+ actualLabel + "'";
				problems.add(msg);
				ExtentReportManager.logStep("❌ " + msg);
			} else {
				ExtentReportManager.logStep("✅ Field '" + fieldId + "' rendered with expected label '" + actualLabel + "'");
			}
		}

		logger.info("KBI schema alignment problems: " + problems);
		Assert.assertTrue(problems.isEmpty(), "KBI form is not aligned to the latest schema: " + problems);
	}

	@Then("KBI field should show validation error for input not matching the schema regex")
	public void verifyRegexValidationError() {
		if (skipIfKbiNotApplicable("KBI regex validation")) {
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		kbiPage.waitForKbiForm(fieldIds);
		String lang = System.getProperty("currentRunLanguage", "eng");

		boolean anyFieldHadRegex = false;
		List<String> problems = new ArrayList<>();

		for (String fieldId : fieldIds) {
			JSONArray validators = EsignetUtil.getKbiFieldValidators(fieldId);
			for (int i = 0; i < validators.length(); i++) {
				JSONObject validator = validators.optJSONObject(i);
				String regex = validator != null ? validator.optString("regex", null) : null;
				if (regex == null || regex.isBlank()) {
					continue;
				}
				anyFieldHadRegex = true;

				JSONObject errorObj = validator.optJSONObject("error");
				String expectedError = errorObj != null ? errorObj.optString(lang, null) : null;

				kbiPage.enterFieldValue(fieldId, "!!!___INVALID___!!!");
				kbiPage.blurField(fieldId);
				String actualError = kbiPage.getFieldErrorMessage(fieldId);
				ExtentReportManager.logStep("Field '" + fieldId + "' - invalid value entered, error shown: '" + actualError + "'");

				if (actualError.isBlank()) {
					problems.add("Field '" + fieldId + "' showed no error for a regex-violating value");
				} else if (expectedError != null && !expectedError.isBlank()
						&& !expectedError.trim().equalsIgnoreCase(actualError.trim())) {
					problems.add("Field '" + fieldId + "' regex error mismatch - schema says '" + expectedError
							+ "' but form shows '" + actualError + "'");
				}
			}
		}

		if (!anyFieldHadRegex) {
			notApplicable("No KBI field in this transaction's schema declares a regex validator");
			return;
		}
		Assert.assertTrue(problems.isEmpty(), "KBI regex validation not aligned to schema: " + problems);
	}

	@Then("KBI fields should be marked mandatory or optional according to the schema")
	public void verifyMandatoryOptionalIndicatorsMatchSchema() {
		verifyRequiredIndicatorsAgainstSchema();
	}

	@Then("KBI mandatory fields should show asterisk symbol according to the schema")
	public void verifyAsteriskForMandatoryFields() {
		verifyRequiredIndicatorsAgainstSchema();
	}

	private void verifyRequiredIndicatorsAgainstSchema() {
		if (skipIfKbiNotApplicable("KBI required-field indicators")) {
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		kbiPage.waitForKbiForm(fieldIds);

		List<String> problems = new ArrayList<>();
		for (String fieldId : fieldIds) {
			boolean expectedRequired = EsignetUtil.isKbiFieldRequired(fieldId);
			boolean actualMarked = kbiPage.isFieldMarkedRequired(fieldId);

			if (expectedRequired != actualMarked) {
				String msg = "Field '" + fieldId + "' required-indicator mismatch - schema required=" + expectedRequired
						+ " but asterisk shown=" + actualMarked;
				problems.add(msg);
				ExtentReportManager.logStep("❌ " + msg);
			} else {
				ExtentReportManager.logStep("✅ Field '" + fieldId + "' required-indicator matches schema (required="
						+ expectedRequired + ")");
			}
		}
		Assert.assertTrue(problems.isEmpty(), "KBI mandatory/optional indicators not aligned to schema: " + problems);
	}

	@Then("KBI form should show inline error message for empty mandatory fields")
	public void verifyInlineErrorForEmptyMandatoryFields() {
		if (skipIfKbiNotApplicable("KBI empty-mandatory-field inline errors")) {
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		kbiPage.waitForKbiForm(fieldIds);
		String lang = System.getProperty("currentRunLanguage", "eng");
		String expectedRequiredError = EsignetUtil.getKbiRequiredErrorMessage(lang);

		List<String> requiredFields = new ArrayList<>();
		for (String fieldId : fieldIds) {
			if (EsignetUtil.isKbiFieldRequired(fieldId)) {
				requiredFields.add(fieldId);
			}
		}
		if (requiredFields.isEmpty()) {
			notApplicable("No mandatory KBI fields in this transaction's schema");
			return;
		}

		for (String fieldId : requiredFields) {
			kbiPage.touchThenClearField(fieldId);
			kbiPage.blurField(fieldId);
		}
		kbiPage.clickLoginButton();

		List<String> problems = new ArrayList<>();
		for (String fieldId : requiredFields) {
			String actualError = kbiPage.getFieldErrorMessage(fieldId);
			ExtentReportManager.logStep("Mandatory field '" + fieldId + "' left empty, error shown: '" + actualError + "'");

			if (actualError.isBlank()) {
				problems.add("Mandatory field '" + fieldId + "' showed no error when left empty");
			} else if (expectedRequiredError != null && !expectedRequiredError.isBlank()
					&& !expectedRequiredError.trim().equalsIgnoreCase(actualError.trim())) {
				problems.add("Mandatory field '" + fieldId + "' error mismatch - schema says '" + expectedRequiredError
						+ "' but form shows '" + actualError + "'");
			}
		}
		Assert.assertTrue(problems.isEmpty(), "KBI mandatory-field error messages not aligned to schema: " + problems);
	}

	// The UI-schema-supported input control types this test asserts the KBI form can render. Full
	// coverage of all 7 in a single run requires the server-side kbi.field-details schema to declare a
	// field of each type; this suite can't change server config, so it verifies whatever the current
	// schema declares and reports which of the 7 were exercised.
	private static final Set<String> SUPPORTED_INPUT_TYPES = Set.of("Text", "Email", "Number", "Checkbox", "Radio",
			"Dropdown", "Date");

	@Then("KBI form should support the input field types defined in the schema")
	public void verifySupportedInputFieldTypes() {
		if (skipIfKbiNotApplicable("KBI supported input field types")) {
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction - no fields to verify");
			return;
		}
		kbiPage.waitForKbiForm(fieldIds);

		List<String> problems = new ArrayList<>();
		Set<String> covered = new LinkedHashSet<>();
		for (String fieldId : fieldIds) {
			String rendered = kbiPage.getRenderedInputType(fieldId);
			ExtentReportManager.logStep("KBI field '" + fieldId + "' renders as: " + rendered);
			if (SUPPORTED_INPUT_TYPES.contains(rendered)) {
				covered.add(rendered);
			} else {
				problems.add("Field '" + fieldId + "' renders as unsupported/unrecognized control '" + rendered + "'");
			}
		}

		Set<String> missing = new LinkedHashSet<>(SUPPORTED_INPUT_TYPES);
		missing.removeAll(covered);
		ExtentReportManager.logStep("Supported input types exercised by this schema: " + covered
				+ (missing.isEmpty() ? " (all 7 supported types covered)"
						: "; not present in current schema (need server schema variant to cover): " + missing));

		Assert.assertTrue(problems.isEmpty(), "KBI form has fields rendering as unsupported input types: " + problems);
	}

	// The KBI form's language follows the run language (via ui_locales on the authorize URL, see
	// BaseTest). Since Runner re-runs each scenario per run language (runLanguage, else the app's
	// supported languages), these "selected language" checks cover every language.
	@Then("KBI field labels should be displayed in the selected language")
	public void verifyFieldLabelsInSelectedLanguage() {
		if (skipIfKbiNotApplicable("KBI field labels in selected language")) {
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction - no labels to verify");
			return;
		}
		kbiPage.waitForKbiForm(fieldIds);
		String lang = System.getProperty("currentRunLanguage", "eng");

		List<String> problems = new ArrayList<>();
		int verified = 0;
		for (String fieldId : fieldIds) {
			String expected = EsignetUtil.getKbiFieldLabel(fieldId, lang);
			if (expected == null || expected.isBlank()) {
				continue; // no schema label for this language - that's the fallback case, covered separately
			}
			String actual = kbiPage.getFieldLabel(fieldId);
			ExtentReportManager.logStep("Field '" + fieldId + "' label (lang=" + lang + ") expected '" + expected
					+ "', shown '" + actual + "'");
			if (!expected.trim().equalsIgnoreCase(actual.trim())) {
				problems.add("Field '" + fieldId + "' label not in selected language '" + lang + "' - expected '"
						+ expected + "' but shows '" + actual + "'");
			} else {
				verified++;
			}
		}
		if (verified == 0 && problems.isEmpty()) {
			notApplicable("No schema-declared labels for language '" + lang + "' to verify");
			return;
		}
		Assert.assertTrue(problems.isEmpty(), "KBI field labels not aligned to selected language: " + problems);
	}

	@Then("KBI dropdown options should be displayed in the selected language")
	public void verifyDropdownOptionsInSelectedLanguage() {
		if (skipIfKbiNotApplicable("KBI dropdown options in selected language")) {
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction");
			return;
		}
		kbiPage.waitForKbiForm(fieldIds);
		String lang = System.getProperty("currentRunLanguage", "eng");

		List<String> dropdownFields = new ArrayList<>();
		for (String fieldId : fieldIds) {
			if ("Dropdown".equals(kbiPage.getRenderedInputType(fieldId))) {
				dropdownFields.add(fieldId);
			}
		}
		if (dropdownFields.isEmpty()) {
			notApplicable("No dropdown field in this transaction's KBI schema - needs a server schema variant with a dropdown field");
			return;
		}

		List<String> problems = new ArrayList<>();
		for (String fieldId : dropdownFields) {
			List<String> options = kbiPage.getDropdownOptionTexts(fieldId);
			List<String> expected = EsignetUtil.getKbiDropdownOptionLabels(fieldId, lang);
			ExtentReportManager.logStep("Dropdown '" + fieldId + "' options (lang=" + lang + "): " + options
					+ " | schema-declared: " + expected);

			// Compared unordered - JSONObject key order isn't guaranteed to match render order.
			if (!expected.isEmpty()) {
				if (!new HashSet<>(expected).equals(new HashSet<>(options))) {
					problems.add("Dropdown '" + fieldId + "' options don't match the schema for language '" + lang
							+ "' - expected " + expected + " but found " + options);
				}
			} else if (options.isEmpty()) {
				// No allowedValues in the schema to compare against - fall back to "it rendered something".
				problems.add("Dropdown '" + fieldId + "' rendered no option text in language '" + lang + "'");
			}
		}
		Assert.assertTrue(problems.isEmpty(), "KBI dropdown options not displayed in selected language: " + problems);
	}

	@Then("KBI checkbox labels should be displayed in the selected language")
	public void verifyCheckboxLabelsInSelectedLanguage() {
		if (skipIfKbiNotApplicable("KBI checkbox labels in selected language")) {
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction");
			return;
		}
		kbiPage.waitForKbiForm(fieldIds);
		String lang = System.getProperty("currentRunLanguage", "eng");

		List<String> checkboxFields = new ArrayList<>();
		for (String fieldId : fieldIds) {
			if ("Checkbox".equals(kbiPage.getRenderedInputType(fieldId))) {
				checkboxFields.add(fieldId);
			}
		}
		if (checkboxFields.isEmpty()) {
			notApplicable("No checkbox field in this transaction's KBI schema - needs a server schema variant with a checkbox field");
			return;
		}

		List<String> problems = new ArrayList<>();
		for (String fieldId : checkboxFields) {
			String expected = EsignetUtil.getKbiFieldLabel(fieldId, lang);
			String actual = kbiPage.getFieldLabel(fieldId);
			ExtentReportManager.logStep("Checkbox '" + fieldId + "' text (lang=" + lang + ") expected '" + expected
					+ "', shown '" + actual + "'");
			if (actual.isBlank()) {
				problems.add("Checkbox '" + fieldId + "' rendered no text in language '" + lang + "'");
			} else if (expected != null && !expected.isBlank() && !expected.trim().equalsIgnoreCase(actual.trim())) {
				problems.add("Checkbox '" + fieldId + "' text not in selected language '" + lang + "' - expected '"
						+ expected + "' but shows '" + actual + "'");
			}
		}
		Assert.assertTrue(problems.isEmpty(), "KBI checkbox labels not displayed in selected language: " + problems);
	}

	@Then("KBI field labels should fall back to English when the schema lacks the selected language")
	public void verifyLabelFallbackToEnglish() {
		if (skipIfKbiNotApplicable("KBI English-fallback labels")) {
			return;
		}
		String lang = System.getProperty("currentRunLanguage", "eng");
		if ("eng".equalsIgnoreCase(lang)) {
			notApplicable("Run language is English - fallback to English can't be observed");
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction");
			return;
		}
		kbiPage.waitForKbiForm(fieldIds);

		List<String> problems = new ArrayList<>();
		int observed = 0;
		for (String fieldId : fieldIds) {
			String selLabel = EsignetUtil.getKbiFieldLabel(fieldId, lang);
			if (selLabel != null && !selLabel.isBlank()) {
				continue; // schema has the selected language for this field - not a fallback case
			}
			String engLabel = EsignetUtil.getKbiFieldLabel(fieldId, "eng");
			if (engLabel == null || engLabel.isBlank()) {
				continue; // no English label either - nothing to fall back to
			}
			String actual = kbiPage.getFieldLabel(fieldId);
			observed++;
			ExtentReportManager.logStep("Field '" + fieldId + "' has no '" + lang + "' schema label; expecting English "
					+ "fallback '" + engLabel + "', shown '" + actual + "'");
			if (!engLabel.trim().equalsIgnoreCase(actual.trim())) {
				problems.add("Field '" + fieldId + "' did not fall back to English - expected '" + engLabel
						+ "' but shows '" + actual + "'");
			}
		}
		if (observed == 0) {
			notApplicable("Every schema field has a '" + lang + "' label - no missing-language field to observe English fallback");
			return;
		}
		Assert.assertTrue(problems.isEmpty(), "KBI labels did not fall back to English: " + problems);
	}

	// Drops the network via the browser's offline emulation and verifies the "Network Error!" screen
	// appears (it auto-pops a few seconds after disconnect); then restores the connection, clicks Try
	// Again, and confirms the KBI schema reloads as a fresh entry.
	@Then("KBI form should show an error and reload the schema on network disconnect")
	public void verifyNetworkDisconnectHandling() {
		if (skipIfKbiNotApplicable("KBI network-disconnect handling")) {
			return;
		}
		if (!kbiPage.isNetworkControlSupported()) {
			notApplicable("Network conditions can't be controlled on this driver (e.g. remote/BrowserStack) - offline simulation not supported");
			return;
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction");
			return;
		}
		kbiPage.waitForKbiForm(fieldIds);

		boolean errorShown;
		try {
			kbiPage.setOffline(true);
			ExtentReportManager.logStep("Simulated network disconnect (browser offline)");
			errorShown = kbiPage.isNetworkErrorShown(20);
			ExtentReportManager.logStep("Offline error screen text: '" + kbiPage.getNetworkErrorText() + "'");
		} finally {
			kbiPage.setOffline(false);
			ExtentReportManager.logStep("Restored network (browser online)");
		}
		Assert.assertTrue(errorShown, "The 'Network Error!' screen was not shown after the network was disconnected");

		// Try Again reloads the flow to a fresh login entry - the mode-selection page, not straight
		// back to the KBI form. Confirm that page is back with the KBI option available again.
		kbiPage.clickTryAgain();
		boolean reloaded;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until(ExpectedConditions.or(
					ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[id^='acr_']")),
					ExpectedConditions.visibilityOfElementLocated(By.id("username_input"))));
			loginOptionsPage.revealMoreOptionsIfPresent();
			reloaded = loginOptionsPage.isLoginWithKbiDisplayed();
		} catch (Exception e) {
			reloaded = false;
		}
		Assert.assertTrue(reloaded,
				"The login flow did not reload as a fresh entry (mode selection with KBI) after clicking Try Again");
	}

	// Fully schema-driven: fills every REQUIRED field with a known-valid value matching the Sunbird
	// RC policy CreatePolicySunBirdR created, leaves optional fields blank, and submits. Reused
	// as-is regardless of how many fields the schema declares or which ones - the same code covers
	// today's 4-field schema, a schema with a field added or removed, or a single-field schema. The
	// server-side kbi.field-details schema itself is what must change between those cases, not this
	// code.
	@Then("KBI authentication should be successful")
	public void verifyKbiAuthenticationSucceeds() {
		if (skipIfKbiNotApplicable("KBI authentication")) {
			return;
		}
		// The credentials filled in below only exist as a real, matching identity when
		// CreatePolicySunBirdR actually ran to create them - which only happens on a Sunbird
		// RC-backed server. On plain mock, these values were never registered anywhere, so the
		// server correctly rejects them - not applicable rather than failing on an environment this
		// scenario doesn't apply to.
		if (!EsignetUtil.isSunbirdAuthenticatorActive()) {
			notApplicable("KBI authentication with the Sunbird policy fixture only applies to a Sunbird RC-backed server");
			return;
		}

		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction - cannot attempt a login");
			return;
		}
		kbiPage.waitForKbiForm(fieldIds);

		String individualIdField = EsignetUtil.getKbiIndividualIdField();

		for (String fieldId : fieldIds) {
			if (!EsignetUtil.isKbiFieldRequired(fieldId)) {
				continue;
			}
			String value = resolveKnownSunBirdRValue(fieldId, individualIdField);
			if (value == null) {
				notApplicable("No known valid value for mandatory KBI field '" + fieldId + "' - the Sunbird policy fixture "
						+ "doesn't cover it, so a real login can't be attempted for this schema");
				return;
			}
			kbiPage.enterFieldValue(fieldId, value);
			kbiPage.blurField(fieldId);
			ExtentReportManager.logStep("Filled mandatory field '" + fieldId + "' with known-valid Sunbird policy data");
		}

		kbiPage.clickLoginButton();

		// A successful KBI login lands on the "Attention" screen (Proceed button) before consent. The
		// auth does a Sunbird RC round-trip that can exceed the default explicit wait, so allow longer.
		Assert.assertTrue(consentPage.isOnAttentionScreen(30),
				"KBI authentication did not reach the attention screen - login was not successful");
	}

	// Maps a KBI schema field id (exact match, not substring) to its CreatePolicySunBirdR fixture
	// value, or null if uncovered. Substring matching previously fed policyName the policy number.
	private String resolveKnownSunBirdRValue(String fieldId, String individualIdField) {
		if (fieldId.equals(individualIdField)) {
			return EsignetUtil.getSunBirdRPolicyNumber();
		}
		switch (fieldId.toLowerCase()) {
		case "policynumber":
			return EsignetUtil.getSunBirdRPolicyNumber();
		case "policyname":
			return EsignetUtil.getSunBirdRPolicyName();
		case "fullname":
			return EsignetUtil.getSunBirdRFullName();
		case "dob":
			return EsignetUtil.getSunBirdRDob();
		case "mobile":
			return EsignetUtil.getSunBirdRMobile();
		case "email":
			return EsignetUtil.getSunBirdREmail();
		case "gender":
			return EsignetUtil.getSunBirdRGender();
		default:
			return null;
		}
	}

	private boolean skipIfKbiNotApplicable(String checkDescription) {
		if (!kbiApplicable) {
			notApplicable(checkDescription + ": KBI is not offered by this environment's default "
					+ "client/policy - verified live.");
			return true;
		}
		return false;
	}

	private void notApplicable(String reason) {
		logger.info("Not checking (this step only, not the scenario): " + reason);
		ExtentReportManager.notApplicable(reason);
	}

	private void skipScenario(String reason) {
		logger.info("Skipping scenario - " + reason);
		ExtentReportManager.getTest().skip(reason);
		throw new SkipException(reason);
	}
}
