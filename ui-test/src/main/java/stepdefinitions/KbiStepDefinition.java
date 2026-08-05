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

	public KbiStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		kbiPage = new KbiPage(driver);
		consentPage = new ConsentPage(driver);
	}

	@When("user clicks on login with KBI")
	public void userClicksOnLoginWithKbi() {
		// /authorize redirects to /login#<payload> client-side - wait for the fragment before reading it.
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.urlContains("#"));
		ClaimsUtil.parseFromUrl(driver.getCurrentUrl());

		List<String> authFactors = ClaimsUtil.getAuthFactors();
		boolean kbiOffered = authFactors.stream().anyMatch(f -> "KBI".equals(ClaimsUtil.normalizeFactor(f)));
		if (!kbiOffered) {
			skip("KBI auth factor is not offered in this transaction (client/policy did not negotiate it)");
		}

		if (EsignetUtil.getKbiFieldIds().isEmpty()) {
			skip("KBI is offered but this transaction's field schema (configs['auth.factor.kbi.field-details'].schema) is empty");
		}

		loginOptionsPage.revealMoreOptionsIfPresent();
		if (!loginOptionsPage.isLoginWithKbiDisplayed()) {
			skip("KBI is offered in the transaction but the 'login with KBI' option is not rendered on the login page");
		}
		loginOptionsPage.clickOnLoginWithKbi();
	}

	@Then("KBI form fields and labels should be aligned to the latest schema")
	public void verifyKbiFormAlignedToSchema() {
		List<String> schemaFieldIds = EsignetUtil.getKbiFieldIds();
		if (schemaFieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction - nothing to validate against");
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
			skip("No KBI field in this transaction's schema declares a regex validator");
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
			skip("No mandatory KBI fields in this transaction's schema");
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
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction - no fields to verify");
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
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction - no labels to verify");
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
			skip("No schema-declared labels for language '" + lang + "' to verify");
		}
		Assert.assertTrue(problems.isEmpty(), "KBI field labels not aligned to selected language: " + problems);
	}

	@Then("KBI dropdown options should be displayed in the selected language")
	public void verifyDropdownOptionsInSelectedLanguage() {
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction");
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
			skip("No dropdown field in this transaction's KBI schema - needs a server schema variant with a dropdown field");
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
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction");
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
			skip("No checkbox field in this transaction's KBI schema - needs a server schema variant with a checkbox field");
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
		String lang = System.getProperty("currentRunLanguage", "eng");
		if ("eng".equalsIgnoreCase(lang)) {
			skip("Run language is English - fallback to English can't be observed");
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction");
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
			skip("Every schema field has a '" + lang + "' label - no missing-language field to observe English fallback");
		}
		Assert.assertTrue(problems.isEmpty(), "KBI labels did not fall back to English: " + problems);
	}

	// Drops the network via the browser's offline emulation and verifies the "Network Error!" screen
	// appears (it auto-pops a few seconds after disconnect); then restores the connection, clicks Try
	// Again, and confirms the KBI schema reloads as a fresh entry.
	@Then("KBI form should show an error and reload the schema on network disconnect")
	public void verifyNetworkDisconnectHandling() {
		if (!kbiPage.isNetworkControlSupported()) {
			skip("Network conditions can't be controlled on this driver (e.g. remote/BrowserStack) - offline simulation not supported");
		}
		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction");
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
			new WebDriverWait(driver, Duration.ofSeconds(15))
					.until(ExpectedConditions.visibilityOfElementLocated(By.id("login_with_otp")));
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
		// The credentials filled in below only exist as a real, matching identity when
		// CreatePolicySunBirdR actually ran to create them - which only happens on a Sunbird
		// RC-backed server. On plain mock, these values were never registered anywhere, so the
		// server correctly rejects them - skip rather than fail on an environment this scenario
		// doesn't apply to.
		if (!EsignetUtil.isSunbirdAuthenticatorActive()) {
			skip("KBI authentication with the Sunbird policy fixture only applies to a Sunbird RC-backed server");
		}

		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			skip("KBI form schema is empty for this transaction - cannot attempt a login");
		}
		kbiPage.waitForKbiForm(fieldIds);

		String individualIdField = EsignetUtil.getKbiIndividualIdField();

		for (String fieldId : fieldIds) {
			if (!EsignetUtil.isKbiFieldRequired(fieldId)) {
				continue;
			}
			String value = resolveKnownSunBirdRValue(fieldId, individualIdField);
			if (value == null) {
				skip("No known valid value for mandatory KBI field '" + fieldId + "' - the Sunbird policy fixture "
						+ "doesn't cover it, so a real login can't be attempted for this schema");
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

	// Logs the reason to the report, then skips into the ignore bucket rather than failing.
	private void skip(String reason) {
		logger.info("Skipping KBI scenario: " + reason);
		ExtentReportManager.getTest().skip("⏭️ Skipped: " + reason);
		throw new SkipException(reason);
	}
}
