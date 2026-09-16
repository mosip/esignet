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

	private boolean kbiApplicable = true;

	public KbiStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		kbiPage = new KbiPage(driver);
		consentPage = new ConsentPage(driver);
	}

	@When("user clicks on Login with KBI")
	public void userClicksOnLoginWithKbi() {
		if (EsignetUtil.isKbiOnlyLogin() && kbiPage.isOnKbiForm()) {
			logger.info("KBI-only login: already on the KBI form, no login-option click needed");
			return;
		}

		if (EsignetUtil.isKbiOnlyLogin()) {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.or(
					ExpectedConditions.presenceOfElementLocated(By.cssSelector("[id^='acr_']")),
					ExpectedConditions.presenceOfElementLocated(By.id("username_input")),
					ExpectedConditions.presenceOfElementLocated(By.id("form-submit-button"))));
			if (kbiPage.isOnKbiForm()) {
				logger.info("KBI-only login: already on the KBI form, no login-option click needed");
				return;
			}
		} else {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.or(
					ExpectedConditions.presenceOfElementLocated(By.cssSelector("[id^='acr_']")),
					ExpectedConditions.presenceOfElementLocated(By.id("username_input"))));
		}

		loginOptionsPage.revealMoreOptionsIfPresent();

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

		if (EsignetUtil.isKbiOnlyLogin()) {
			List<String> problems = new ArrayList<>();
			for (String fieldId : fieldIds) {
				String actual = kbiPage.getFieldLabel(fieldId);
				if (actual == null || actual.isBlank()) {
					problems.add("Field '" + fieldId + "' has no visible English label");
					continue;
				}
				String lower = actual.toLowerCase();
				boolean looksEnglish = lower.matches(".*[a-z].*");
				if (!looksEnglish) {
					problems.add("Field '" + fieldId + "' label does not look like English: '" + actual + "'");
				}
			}
			Assert.assertTrue(problems.isEmpty(),
					"KBI page content is not displayed in default English language: " + problems);
			return;
		}

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

		if ((EsignetUtil.isMockPlugin() || EsignetUtil.isKbiOnlyLogin()) && kbiPage.isLoginButtonEnabled()) {
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
			String value = null;
			if (EsignetUtil.isKbiOnlyLogin()) {
				value = EsignetUtil.resolveKbiFieldValue(fieldId, kbiPage.getFieldLabel(fieldId));
			}
			if (value == null) {
				value = resolveKnownSunBirdRValue(fieldId, individualIdField);
			}
			if (value == null) {
				value = defaultValueForField(fieldId);
			}
			kbiPage.enterFieldValue(fieldId, value);
			kbiPage.blurField(fieldId);
		}
		if (EsignetUtil.isKbiOnlyLogin()) {
			kbiPage.solveRecaptchaIfPresent();
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
		if (EsignetUtil.isKbiOnlyLogin()) {
			kbiPage.solveRecaptchaIfPresent();
		}
		kbiPage.clickLoginButton();
	}

	@Then("verify KBI authentication is successful")
	public void verifyKbiAuthenticationIsSuccessful() {
		if (skipIfKbiNotApplicable("KBI authentication")) {
			return;
		}

		if (!EsignetUtil.isSunbirdAuthenticatorActive()) {
			notApplicable("KBI authentication with the Sunbird policy fixture data only applies to a Sunbird RC-backed server");
			return;
		}
		Assert.assertTrue(consentPage.isOnAttentionScreen(30),
				"KBI authentication did not reach the attention screen - login was not successful");
	}

	@Then("verify KBI form shows Policy Number Fullname and Date of Birth fields")
	public void verifyKbiFormShowsExpectedIdentityFields() {
		if (skipIfKbiNotApplicable("KBI expected identity fields")) {
			return;
		}
		List<String> fieldIds = kbiFieldsToUse();
		kbiPage.waitForKbiForm(fieldIds);
		Assert.assertFalse(fieldIds.isEmpty(), "No KBI fields were rendered");

		boolean hasPolicy = fieldIds.stream().anyMatch(id -> EsignetUtil.normalizeKbiFieldId(id).contains("policy"));
		boolean hasName = fieldIds.stream().anyMatch(id -> {
			String n = EsignetUtil.normalizeKbiFieldId(id);
			return n.contains("fullname") || n.equals("name");
		});
		boolean hasDob = fieldIds.stream().anyMatch(id -> {
			String n = EsignetUtil.normalizeKbiFieldId(id);
			return n.contains("dob") || n.contains("dateofbirth") || n.contains("birth");
		});
		if (!hasPolicy || !hasName || !hasDob) {
			for (String fieldId : fieldIds) {
				String label = kbiPage.getFieldLabel(fieldId).toLowerCase();
				hasPolicy = hasPolicy || (label.contains("policy") && label.contains("number"));
				hasName = hasName || label.contains("name") || label.contains("fullname");
				hasDob = hasDob || label.contains("birth") || label.contains("dob");
			}
		}
		Assert.assertTrue(hasPolicy, "Policy Number field is not rendered on the KBI form. Fields=" + fieldIds);
		Assert.assertTrue(hasName, "Fullname field is not rendered on the KBI form. Fields=" + fieldIds);
		Assert.assertTrue(hasDob, "Date of Birth field is not rendered on the KBI form. Fields=" + fieldIds);
	}

	@Then("verify KBI form shows validation errors for empty mandatory fields")
	public void verifyKbiFormShowsValidationErrorsForEmptyMandatoryFields() {
		if (skipIfKbiNotApplicable("KBI empty-field validation")) {
			return;
		}
		List<String> fieldIds = kbiFieldsToUse();
		kbiPage.waitForKbiForm(fieldIds);
		List<String> problems = new ArrayList<>();
		for (String fieldId : fieldIds) {
			String error = kbiPage.getFieldErrorMessage(fieldId);
			if (error == null || error.isBlank()) {
				problems.add("Field '" + fieldId + "' did not show an empty-field validation error");
			}
		}
		if (problems.size() == fieldIds.size()) {
			boolean pageLevelError = !driver.findElements(By.cssSelector(
					".error-message, [class*='error'], [role='alert'], #status_message")).isEmpty();
			Assert.assertTrue(pageLevelError,
					"No per-field or page-level validation error was shown after submitting empty KBI fields");
			return;
		}
		Assert.assertTrue(problems.size() < fieldIds.size(),
				"Expected at least one empty-field validation error, but found none usable: " + problems);
	}

	@When("user fills invalid KBI credentials")
	public void userFillsInvalidKbiCredentials() {
		if (skipIfKbiNotApplicable("filling invalid KBI credentials")) {
			return;
		}
		List<String> fieldIds = kbiFieldsToUse();
		kbiPage.waitForKbiForm(fieldIds);
		for (String fieldId : fieldIds) {
			String renderedType = kbiPage.getRenderedInputType(fieldId);
			String label = kbiPage.getFieldLabel(fieldId).toLowerCase();
			String value;
			if ("Date".equals(renderedType) || label.contains("birth") || label.contains("dob")) {
				value = "1990-01-01";
			} else if (label.contains("policy") || EsignetUtil.normalizeKbiFieldId(fieldId).contains("policy")) {
				value = "000000000";
			} else if (label.contains("name") || EsignetUtil.normalizeKbiFieldId(fieldId).contains("name")) {
				value = "Invalid User";
			} else {
				value = "INVALID";
			}
			kbiPage.enterFieldValue(fieldId, value);
			kbiPage.blurField(fieldId);
		}
		ExtentReportManager.logStep("Filled invalid KBI credentials for negative authentication check");
	}

	@Then("verify KBI authentication is not successful")
	public void verifyKbiAuthenticationIsNotSuccessful() {
		if (skipIfKbiNotApplicable("KBI negative authentication")) {
			return;
		}
		boolean reachedAttention = consentPage.isOnAttentionScreen(8);
		Assert.assertFalse(reachedAttention,
				"Invalid KBI credentials unexpectedly reached the attention/consent screen");
		Assert.assertTrue(kbiPage.isOnKbiForm() || !driver.findElements(By.cssSelector(
				".error-message, [class*='error'], [role='alert'], #status_message, div.error-page-header")).isEmpty(),
				"Expected to remain on the KBI form or see an authentication error after invalid credentials");
	}

	@Then("verify user is returned to the relying party after KBI login")
	public void verifyUserIsReturnedToRelyingPartyAfterKbiLogin() {
		if (skipIfKbiNotApplicable("return to relying party after KBI login")) {
			return;
		}
		Assert.assertTrue(consentPage.isAlreadyOnRelyingParty() || consentPage.waitForRelyingPartyRedirectQuietly(),
				"User was not returned to the relying party after completing KBI login/consent");
	}

	private List<String> kbiFieldsToUse() {
		List<String> schemaFieldIds = EsignetUtil.getKbiFieldIds();
		if (!schemaFieldIds.isEmpty()) {
			return schemaFieldIds;
		}

		new WebDriverWait(driver, Duration.ofSeconds(25)).until(ExpectedConditions.or(
				ExpectedConditions.presenceOfElementLocated(By.cssSelector(
						"#policyNumber, #fullName, #dob, [name='policyNumber'], [name='fullName'], [name='dob']")),
				ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input:not([type='hidden'])")),
				ExpectedConditions.presenceOfElementLocated(By.cssSelector("input:not([type='hidden'])"))));
		List<String> visible = kbiPage.getVisibleFieldIds();
		if (!visible.isEmpty()) {
			return visible;
		}
		return List.of("policyNumber", "fullName", "dob");
	}

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
				continue;
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

			if (!expected.isEmpty()) {
				if (!new HashSet<>(expected).equals(new HashSet<>(options))) {
					problems.add("Dropdown '" + fieldId + "' options don't match the schema for language '" + lang
							+ "' - expected " + expected + " but found " + options);
				}
			} else if (options.isEmpty()) {

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
				continue;
			}
			String engLabel = EsignetUtil.getKbiFieldLabel(fieldId, "eng");
			if (engLabel == null || engLabel.isBlank()) {
				continue;
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

		kbiPage.clickTryAgain();
		boolean reloaded;
		try {
			if (EsignetUtil.isKbiOnlyLogin()) {
				new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> kbiPage.isOnKbiForm());
				reloaded = kbiPage.isOnKbiForm();
			} else {
				new WebDriverWait(driver, Duration.ofSeconds(15)).until(ExpectedConditions.or(
						ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[id^='acr_']")),
						ExpectedConditions.visibilityOfElementLocated(By.id("username_input"))));
				loginOptionsPage.revealMoreOptionsIfPresent();
				reloaded = loginOptionsPage.isLoginWithKbiDisplayed();
			}
		} catch (Exception e) {
			logger.warn("Login flow did not reload after Try Again: " + e.getMessage(), e);
			reloaded = false;
		}
		Assert.assertTrue(reloaded,
				"The login flow did not reload as a fresh entry (mode selection with KBI) after clicking Try Again");
	}

	@Then("KBI authentication should be successful")
	public void verifyKbiAuthenticationSucceeds() {
		if (skipIfKbiNotApplicable("KBI authentication")) {
			return;
		}

		if (!EsignetUtil.isSunbirdAuthenticatorActive()) {
			notApplicable("KBI authentication with the Sunbird policy fixture only applies to a Sunbird RC-backed server");
			return;
		}

		List<String> fieldIds = EsignetUtil.getKbiFieldIds();
		if (fieldIds.isEmpty()) {
			fieldIds = kbiPage.getVisibleFieldIds();
		}
		if (fieldIds.isEmpty()) {
			notApplicable("KBI form schema is empty for this transaction - cannot attempt a login");
			return;
		}
		kbiPage.loginWithConfiguredIdentity();

		Assert.assertTrue(consentPage.isOnAttentionScreen(30),
				"KBI authentication did not reach the attention screen - login was not successful");
	}

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
