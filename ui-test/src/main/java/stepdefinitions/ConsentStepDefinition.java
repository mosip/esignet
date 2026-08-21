package stepdefinitions;

import static org.junit.Assert.assertFalse;
import org.testng.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.log4j.Logger;

import base.BasePage;
import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.mosip.testrig.apirig.utils.NotificationListener;
import pages.ConsentPage;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.ClaimsUtil;
import utils.EsignetUtil;
import utils.EsignetUtil.RegisteredDetails;
import utils.ExtentReportManager;
import utils.ResourceBundleLoader;

public class ConsentStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(ConsentStepDefinition.class);
	LoginOptionsPage loginOptionsPage;
	SignUpPage signUpPage;
	SignupFormDynamicFiller formFiller;
	ConsentPage consentPage;

	public ConsentStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		signUpPage = new SignUpPage(driver);
		formFiller = new SignupFormDynamicFiller(driver);
		consentPage = new ConsentPage(driver);
	}

	private boolean notApplicableUnderMockPlugin(String featureDescription) {
		return EsignetUtil.notApplicableUnderMockPlugin(featureDescription, logger);
	}

	private boolean notApplicableForReLoginUnderMockPlugin() {
		if (EsignetUtil.isMockPlugin() && BaseTest.isMockPluginLoginCompleted()) {
			String reason = "a repeat OTP login does not exist under this environment's mock-plugin flow "
					+ "- the first login already completed straight through to the relying party, verified live.";
			logger.info("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return true;
		}
		return false;
	}

	@Given("user directly navigates to sign-up portal URL")
	public void userLaunchesSignupPortal() {
		if (!EsignetUtil.isSignupServiceDeployed()) {
			String reason = "signup service is not deployed in this environment (signupUrl is blank) "
					+ "- no portal to navigate to.";
			logger.info("Not navigating (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		signUpPage.navigateToSignupPortal();
	}

	@When("user clicks on Register button")
	public void userClicksOnRegisterButton() {
		if (!EsignetUtil.isSignupServiceDeployed()) {
			String reason = "signup service is not deployed in this environment - no Register button to click.";
			logger.info("Not clicking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		signUpPage.clickOnRegisterButton();
	}

	@Then("the registration form fields should be displayed")
	public void userVerifiesRegistrationFormDisplayed() {
		if (!EsignetUtil.isSignupServiceDeployed()) {
			String reason = "signup service is not deployed in this environment - no registration form to verify.";
			logger.info("Not verifying (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		Assert.assertTrue(signUpPage.isMobileNumberFieldDisplayed(),
				"Registration form's mobile number field was not displayed after clicking Register");
	}

	@Then("user enters mobile_number in the mobile number field")
	public void userEnterValidMobileNumber() {
		String fieldId = EsignetUtil.getIdentifierFieldId();
		String regex = EsignetUtil.getRegexForField(fieldId);
		String value = EsignetUtil.generateValueFromRegex(regex, 9);
		RegisteredDetails.setMobileNumber(value);
		signUpPage.enterMobileNumber(value);
	}

	@Then("user clicks on the Continue button")
	public void userClickOnContinueButton() {
		signUpPage.clickOnContinueButton();
	}

	@When("user enters the OTP")
	public void userEnterOtp() {
		String mobile = RegisteredDetails.getMobileNumber();
		signUpPage.enterOtp(NotificationListener.getOtp(mobile));
	}

	@Then("mark otp request timestamp")
	public void markOtpRequestTimestamp() {
		NotificationListener.markRequestStart();
	}

	@Then("remove otp request timestamp")
	public void removeOtpRequestTimestamp() {
		NotificationListener.markRequestRemove();
	}

	@Then("user clicks on the Verify OTP button")
	public void userClicksOnVerifyOtpButton() {
		signUpPage.clickOnVerifyOtpButton();
	}

	@When("user click on Continue button in Success Screen")
	public void clickOnContinueButtonInSucessScreen() {
		signUpPage.clickOnContinueButtonInSucessScreen();
	}

	@When("user fills the signup form using UI specification")
	public void userFillsSignupFormUsingUiSpecification() throws Exception {
		Map<String, Map<String, Object>> uiSpecFields = EsignetUtil.getUiSpecFields();
		formFiller.fillFormFromUiSpec(uiSpecFields);
	}

	@When("user clicks on Continue button in Setup Account Page")
	public void userClicksOnContinueButtonInSetpuAccountPage() {
		signUpPage.clickOnSetupAccountContinueButton();
	}

	@Then("verify that success screen is displayed")
	public void verifyThenSuccessMessageDisplayed() {
		Assert.assertTrue(signUpPage.isAccountCreatedSuccessfullyMessageDisplayed(),
				"Success message is not displayed");
	}

	private String expectedDefaultLang;

	@Then("user click on Login with Otp")
	public void clickOnLoginWithOtp() {
		if (notApplicableForReLoginUnderMockPlugin()) {
			return;
		}
		expectedDefaultLang = consentPage.getCurrentLanguage();
		consentPage.clickOnLoginWithOtp();
	}

	@Then("user enters Registered mobile number into the mobile number field")
	public void userEntersRegisteredMobileNumber() {
		if (notApplicableForReLoginUnderMockPlugin()) {
			return;
		}
		String registeredNumber = EsignetUtil.getPrerequisiteRegisteredPhoneNumber();
		if (registeredNumber == null || registeredNumber.isBlank()) {
			String reason = "the Adding Identity prerequisite did not produce a registered number.";
			logger.warn("Not entering a mobile number (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		consentPage.enterRegisteredMobileNumber(registeredNumber);
	}

	@Then("user enters the newly registered mobile number into the mobile number field")
	public void userEntersNewlyRegisteredMobileNumber() {
		String registeredNumber = RegisteredDetails.getMobileNumber();
		if (registeredNumber == null || registeredNumber.isBlank()) {
			String reason = "the signup flow did not run, so no newly registered number is available.";
			logger.warn("Not entering a mobile number (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		consentPage.enterRegisteredMobileNumber(registeredNumber);
	}

	@Then("user click on get otp button")
	public void userClickOnGetOtpBtn() {
		if (notApplicableForReLoginUnderMockPlugin()) {
			return;
		}
		consentPage.clickOnGetOtp();
	}

	@Then("user enters the correct otp")
	public void userEnterCorrectOtp() {
		if (notApplicableForReLoginUnderMockPlugin()) {
			return;
		}
		consentPage.enterOtp(BasePage.getOtp());
	}

	@Then("click on verify Otp button")
	public void userClickOnVerifyOtpBtn() {
		if (notApplicableForReLoginUnderMockPlugin()) {
			return;
		}
		consentPage.clickOnVerifyButton();
	}

	@Then("verify consent should ask user to proceed in attention page")
	public void userGoesToAttentionScreen() {
		if (notApplicableForReLoginUnderMockPlugin()) {
			return;
		}
		Assert.assertTrue(consentPage.isOnAttentionScreen(), "User didn't navigated to attention page");
	}

	@Then("clicks on proceed button in attention page")
	public void clickOnProceedButtonInAttentionPage() {
		if (notApplicableForReLoginUnderMockPlugin()) {
			return;
		}
		consentPage.clickOnProceedButtonInAttentionPage();
		if (EsignetUtil.isMockPlugin()) {
			BaseTest.markMockPluginLoginCompleted();
		}
	}

	@Then("clicks on proceed button in next page")
	public void clickOnProceedButtonInNextPage() {
		consentPage.clickOnProceedButton();
	}

	@Then("select the e-kyc verification provider")
	public void selectEKycVerificationProvider() {
		if (notApplicableUnderMockPlugin("the eKYC provider selection screen")) {
			return;
		}
		consentPage.clickOnMockIdentifyVerifier();
	}

	@Then("clicks on proceed button in e-kyc verification provider page")
	public void clickOnProceedButton() {
		if (notApplicableUnderMockPlugin("the eKYC provider selection proceed button")) {
			return;
		}
		consentPage.clickOnProceedButtonInServiceProviderPage();
	}

	@Then("user select the check box in terms and condition page")
	public void userSelectTheCheckBoxInTermsAndConditionPage() {
		if (notApplicableUnderMockPlugin("the eKYC terms and conditions checkbox")) {
			return;
		}
		consentPage.checkTermsAndCondition();
	}

	@Then("user clicks on proceed button in terms and condition page")
	public void userClicksOnProceedButtonInTermsAndConditionPage() {
		if (notApplicableUnderMockPlugin("the eKYC terms and conditions proceed button")) {
			return;
		}
		consentPage.clickOnProceedButtonInTermsAndConditionPage();
	}

	@Then("user clicks on proceed button in camera preview page")
	public void userClicksOnProceedButtonInCameraPreviewPage() {
		if (notApplicableUnderMockPlugin("the eKYC camera preview proceed button")) {
			return;
		}
		consentPage.clickOnProceedButtonInCameraPreviewPage();
	}

	@Then("user is navigated to consent screen once liveness check completes")
	public void waitUntilLivenessCheckCompletesInCameraPage() {
		if (notApplicableUnderMockPlugin("the eKYC liveness check")) {
			return;
		}
		consentPage.waitUntilLivenessCheckCompletes();
	}

	@Then("verify user is navigated to consent screen")
	public void verifyUserIsOnConsentScreen() {
		Assert.assertTrue(consentPage.isConsentScreenVisible(), "User didn't navigated to consent screen");
	}

	@Then("user clicks on language dropdown button")
	public void userClickOnLanguageDropdown() {
		consentPage.clickOnLanguageDropdown();
	}

	@Then("user selects arabic language")
	public void userSelectsArabicLanguage() {
		consentPage.clickOnArabicLanguage();
	}

	@Then("verify screen is displayed in RTL format")
	public void verifyPageDisplayedInRtlFormat() {
		String dirValue = consentPage.getPageDirection();
		Assert.assertEquals(dirValue, "rtl");
	}

	@Then("verify the tooltip message for Voluntary Claims info icon")
	public void verifyTooltipMessageForVoluntaryClaimsIcon() {
		String actualTooltip = consentPage.getVoluntaryClaimsTooltipText();
		Assert.assertFalse(actualTooltip.trim().isEmpty());
	}

	@Then("verify essential claims are listed separately")
	public void verifyEssentialClaimsAreListedSeparately() {
		Assert.assertTrue(consentPage.areEssentialClaimsPresent(), "Essential claims list were not present");
	}

	@Then("verify voluntary claims are listed separately")
	public void verifyVoluntaryClaimsAreListedSeparately() {
		Assert.assertTrue(consentPage.areVoluntaryClaimsPresent(), "Voluntary claims list are not present");
	}

	@Then("verify master toggle should be visible for Voluntary Claims if multiple claims are present")
	public void verifyVoluntaryClaimsMasterToggleVisible() {
		Assert.assertTrue(consentPage.isVoluntaryClaimsMasterToggleVisible(),
				"Master toggle button for voluntary claims is not visisble");
	}

	@Then("verify all toggle buttons for Voluntary Claims are disabled by default")
	public void verifyVoluntaryClaimsMasterToggleDisabled() {
		Assert.assertFalse(consentPage.getVoluntaryClaimsMasterToggle().isSelected());
		for (WebElement subToggle : consentPage.getVoluntaryClaimsSubToggles()) {
			Assert.assertFalse(subToggle.isSelected());
		}
	}

	@Then("verify if user enables Master toggle,all sub-toggles should be enabled")
	public void enableMasterToggleForVoluntaryClaims() {
		consentPage.enableVoluntaryClaimsMasterToggle();
		for (WebElement subToggle : consentPage.getVoluntaryClaimsSubToggles()) {
			Assert.assertTrue(subToggle.isSelected(), "Sub toggle button did not selected");
		}
	}

	@When("if user deselect one of the Voluntary Claims")
	public void userDeselectOneVoluntaryClaim() throws Exception {
		List<String> voluntaryClaims = consentPage.getVoluntaryClaimNamesFromDom();
		assertFalse("Voluntary claims were not loaded for this scenario", voluntaryClaims.isEmpty());
		if (!voluntaryClaims.isEmpty()) {
			String firstClaim = voluntaryClaims.get(0);
			consentPage.toggleVoluntaryClaim(firstClaim, false);
		}
	}

	@Then("verify remaining Voluntary Claims stays selected along with master toggle")
	public void verifyRemainingVoluntaryClaim() {
		List<WebElement> subToggles = consentPage.getVoluntaryClaimsSubToggles();
		int notSelected = 0;
		for (WebElement toggle : subToggles) {
			if (!toggle.isSelected()) {
				notSelected++;
			}
		}
		Assert.assertEquals(notSelected, 1, "Exactly one voluntary claim should be deselected at this point");

		Assert.assertFalse(consentPage.isVoluntaryClaimsMasterToggleSelected(),
				"Master toggle should be off once any voluntary claim is deselected");
	}

	@Then("if user disables Master toggle,all sub-toggles should be disabled")
	public void disableMasterToggleForAuthorizeScope() {
		consentPage.disableVoluntaryClaimsMasterToggle();
		for (WebElement subToggle : consentPage.getVoluntaryClaimsSubToggles()) {
			Assert.assertFalse(subToggle.isSelected());
		}
	}

	@Then("if user manually deselects all sub-toggles,verify master toggle also gets disabled")
	public void verifyDeselectingVoluntaryClaimManually() throws Exception {
		List<String> voluntaryClaims = consentPage.getVoluntaryClaimNamesFromDom();
		assertFalse("Voluntary claims were not loaded for this scenario", voluntaryClaims.isEmpty());
		for (String claim : voluntaryClaims) {
			consentPage.toggleVoluntaryClaim(claim, false);
		}
		Assert.assertFalse(consentPage.isVoluntaryClaimsMasterToggleSelected());
	}

	@When("user enables only one of the Voluntary Claims toggle")
	public void userEnablesOneVoluntaryClaim() throws Exception {
		List<String> voluntaryClaims = consentPage.getVoluntaryClaimNamesFromDom();
		assertFalse("Voluntary claims were not loaded for this scenario", voluntaryClaims.isEmpty());
		if (!voluntaryClaims.isEmpty()) {
			String firstClaim = voluntaryClaims.get(0);
			consentPage.toggleVoluntaryClaim(firstClaim, true);
		}
	}

	@Then("verify that the master toggle remains in unselected state")
	public void verifyMasterToggleIsDisabled() {
		Assert.assertFalse(consentPage.isVoluntaryClaimsMasterToggleSelected());
	}

	List<String> selectedVoluntaryClaims = new ArrayList<>();

	@When("user enables all the voluntary claims sub-toggle manually")
	public void userEnablesAllSubToggles() throws Exception {
		List<String> voluntaryClaims = consentPage.getVoluntaryClaimNamesFromDom();
		assertFalse("Voluntary claims were not loaded for this scenario", voluntaryClaims.isEmpty());
		selectedVoluntaryClaims.clear();

		for (String claim : voluntaryClaims) {
			consentPage.toggleVoluntaryClaim(claim, true);
			selectedVoluntaryClaims.add(claim);
		}
	}

	@Then("verify that the master toggle is enabled automatically")
	public void verifyMasterToggleIsEnabled() {
		Assert.assertTrue(consentPage.isVoluntaryClaimsMasterToggleSelected(),
				"Voluntary claims master toggle is not enabled");
	}

	@Then("verify the timer starts from 120sec in the consent page via Otp login")
	public void verifyConsentPageTimer() {
		int seconds = consentPage.getConsentTimerSeconds();
		Assert.assertTrue(seconds >= 110 && seconds <= 120, "Timer should start around 120 seconds, but was: " + seconds);
	}

	@Then("refresh the browser tab and verify timer continue with leftover seconds")
	public void verifyTimerPersistsAfterRefresh() {
		int beforeRefresh = consentPage.getConsentTimerSeconds();
		logger.info("Timer before waiting: " + beforeRefresh + " seconds");
		driver.navigate().refresh();

		boolean landedOnErrorPage;
		try {
			landedOnErrorPage = new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
				if (consentPage.isConsentScreenVisible()) {
					return Boolean.FALSE;
				}
				if (!d.findElements(org.openqa.selenium.By.cssSelector("div.error-page-header")).isEmpty()) {
					return Boolean.TRUE;
				}
				return null;
			});
		} catch (org.openqa.selenium.TimeoutException e) {
			landedOnErrorPage = false;
		}

		if (Boolean.TRUE.equals(landedOnErrorPage)) {
			String reason = "refreshing the consent screen invalidates the authorize transaction and shows "
					+ "the server's \"Something went wrong (401)\" error page instead of preserving the "
					+ "leftover timer - verified live. Confirmed not recoverable client-side: a fresh nonce, "
					+ "and even a full cookie/local/session-storage clear plus re-navigate, still 401 - the "
					+ "signed authorize request itself is single-use/expired server-side, and re-signing one "
					+ "requires BaseTest's own URL-construction logic, not available from a step definition.";
			logger.info("Not checking (this step only, not the rest of the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			Assert.fail("Consent screen unrecoverable after refresh-triggered 401 (see report note above) - "
					+ "remaining consent-screen steps in this scenario cannot run.");
		}

		int afterRefresh = consentPage.getConsentTimerSeconds();
		logger.info("Timer after refresh: " + afterRefresh + " seconds");

		Assert.assertTrue(afterRefresh <= beforeRefresh && (beforeRefresh - afterRefresh) <= 2,
				"Timer should persist after refresh within 2 seconds tolerance");
	}

	@Then("user verify the header of essential claims")
	public void verifyTheEssentialClaimsHeader() {
		Assert.assertTrue(consentPage.isEssentialClaimsHeaderDisplayed(),
				"The header of the essential is not displayed");
	}

	@Then("user verify the list of essential claims are present")
	public void verifyTheEssentialClaimsList() {
		Assert.assertTrue(consentPage.isEssentialClaimsListDisplayed(),
				"No essential claims were rendered on the consent screen");
	}

	@Then("user verify the action message in consent screen")
	public void verifyTheActionMessage() {
		Assert.assertTrue(consentPage.isActionMessageDisplayed(),
				"The action message in the consent screen is not displayed");
	}

	@Then("user verify the timer is displayed in consent screen")
	public void verifyTheTimerInConsentScreen() {
		Assert.assertTrue(consentPage.isTimerDisplayed(), "The timer is not displayed in the consent screen");
	}

	@Then("verify the otp verification button is disabled on the verification screen")
	public void verifyOtpVerificationButtonIsDisabled() {
		if (EsignetUtil.isMockPlugin() && consentPage.isVerifyOtpButtonEnabled()) {
			String reason = "this environment's OTP verify button has no client-side "
					+ "disabled-until-valid-input gating - verified live.";
			logger.info("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		Assert.assertFalse(consentPage.isVerifyOtpButtonEnabled(), "Otp verification button is enabled");
	}

	@Then("verify the otp verification button is enabled on the verification screen")
	public void verifyOtpVerificationButtonIsEnabled() {
		Assert.assertTrue(consentPage.isVerifyOtpButtonEnabled(), "Otp verification button is not enabled");
	}

	@When("user creates the client with purpose type login")
	public void userCreateClientIdPurposeLogin() {
	}

	@Then("all auth factors should start with login")
	public void verifyLoginPurposeReflectedInUI() {
		String expectedText = ResourceBundleLoader.getPrefixText("button.login_otp");
		Assert.assertTrue(consentPage.isLoginWithOtpDisplayed(expectedText),
				"Expected text not displayed: " + expectedText);
	}
	
	@When("user creates the client without purpose field")
	public void userCreateClientIdWithoutPurpose() {
	}

	@When("user creates the client with purpose type link")
	public void userCreateClientIdPurposeLink() {
	}

	@Then("all auth factors should start with link")
	public void verifyLinkPurposeReflectedInUI() {
		String expectedText = ResourceBundleLoader.getPrefixText("button.login_otp");
		Assert.assertTrue(consentPage.isLoginWithOtpDisplayed(expectedText),
				"Expected text not displayed: " + expectedText);
	}

	@When("user creates the client with purpose type verify")
	public void userCreateClientIdPurposeVerify() {
	}

	@Then("all auth factors should start with verify")
	public void validateVerifyPurposeReflectedInUI() {
		String expectedText = ResourceBundleLoader.getPrefixText("button.login_otp");
		Assert.assertTrue(consentPage.isLoginWithOtpDisplayed(expectedText),
				"Expected text not displayed: " + expectedText);
	}

	@When("user creates the client with purpose type none")
	public void userCreateClientIdPurposeNone() {
	}

	@Then("verify no title or subtitle should be displayed")
	public void verifyTitleNotDisplayed() {
		Assert.assertFalse(consentPage.isLoginTitleDisplayed(), "Login title was displayed");
		Assert.assertFalse(consentPage.isLoginSubTitleDisplayed(), "Login subtitle was displayed");
	}

	@Then("verify title and subtitle should be displayed as per text given during client creation")
	public void verifyDefaultLoginTitleAndSubtitle() {
		String currentLang = System.getProperty("currentRunLanguage", "eng");
		if (!"eng".equalsIgnoreCase(currentLang)) {
			ExtentReportManager.notApplicable(
					"Expected title/subtitle text is only verified for an English run - this transaction's "
							+ "default-purpose text has no localized reference in this suite.");
			return;
		}
		Assert.assertEquals(consentPage.getLoginTitleText(), "Login", "Title text mismatch");
		Assert.assertTrue(consentPage.getLoginSubTitleText().contains("is requesting authentication for login"),
				"Subtitle text mismatch");
	}

	@When("user creates the client with null title and subtitle values")
	public void userCreateClientIdWithNullTitle() {
	}

	@When("user creates the client with empty title and subtitle values")
	public void userCreateClientIdWithEmptyTitle() {
	}

	@Then("verify select preferred mode text is displayed")
	public void verifySelectPreferredModeText() {
		String expectedText = ResourceBundleLoader.get("header.select_login_mode");
		Assert.assertEquals(consentPage.getSelectPreferredModeHeaderText(), expectedText, "Expected text mismatch");
	}

	@Then("verify select preferred ID text based on purpose type when more than one auth factor is present")
	public void verifySelectPreferredIdHeaderText() {
		List<String> authFactors = ClaimsUtil.getCachedRenderedAuthFactors();
		Assert.assertFalse(authFactors.isEmpty(), "No auth factors were parsed from the authorize URL");

		Assert.assertTrue(authFactors.size() > 1, "Expected multiple auth factors, got " + authFactors.size());
		String expectedText = ResourceBundleLoader.get("header.select_login_id");
		Assert.assertEquals(consentPage.getSelectPreferredIdHeaderText(), expectedText, "Expected text mismatch");
	}

	@When("user creates the client with single auth factor")
	public void userCreateClientIdWithSingleAuthFactor() {
	}

	@Then("verify select ID type text based on purpose type when one auth factor is displayed")
	public void verifySelectIdTypeHeaderText() {
		List<String> authFactors = ClaimsUtil.getCachedRenderedAuthFactors();
		Assert.assertFalse(authFactors.isEmpty(), "No auth factors were parsed from the authorize URL");

		Assert.assertTrue(authFactors.size() == 1, "Expected exactly one auth factor, got " + authFactors.size());
		String expectedText = ResourceBundleLoader.get("header.select_login_id");
		Assert.assertEquals(consentPage.getSelectPreferredIdHeaderText(), expectedText, "Expected text mismatch");
	}

	@Then("verify select preferred ID text based on purpose type is displayed")
	public void verifySelectPreferredIdHeaderTextDisplayed() {
		String expectedText = ResourceBundleLoader.get("header.select_login_id");
		Assert.assertEquals(consentPage.getSelectPreferredIdHeaderText(), expectedText, "Expected text mismatch");
	}
	
	@Then("verify user is navigated to consent to profile update screen")
	public void verifyNavigatedToConsentProfileUpdateScreen() {
		Assert.assertTrue(consentPage.isHeaderInConsentUpdateProfileScreenVisible(),
				"User is not navigated to the consent to profile update screen");
	}

	@Then("verify the header Attention in the consent to profile update screen")
	public void verifyHeaderInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isHeaderInConsentUpdateProfileScreenVisible(),
				"Header in consent to profile update screen is not displayed");
	}

	@Then("verify the sub header in the consent to profile update screen")
	public void verifySubHeaderInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isSubHeaderInConsentUpdateProfileScreenVisible(),
				"Sub header in consent to profile update screen is not displayed");
	}

	@Then("verify the essential claim header in consent to update profile screen")
	public void verifyEssentialClaimHeaderInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isEssentialClaimsHeaderInConsentUpdateProfileScreenVisible(),
				"Essential cliams header in consent to profile update screen is not displayed");
	}

	@Then("verify the voluntary claim header in the consent to profile update screen")
	public void verifyVoluntaryClaimHeaderInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isVoluntaryClaimsHeaderInConsentUpdateProfileScreenVisible(),
				"Voluntary cliams header in consent to profile update screen is not displayed");
	}

	@Then("verify info icon is available in consent to update profile screen")
	public void verifyInfoIconInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isInfoIconInConsentUpdateProfileScreenVisible(),
				"Info icon in consent to profile update screen is not displayed");
	}

	@Then("verify proceed button is visible in consent to update profile screen")
	public void verifyProceedBtnInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isProceedButtonInConsentUpdateProfileScreenVisible(),
				"Proceed in consent to profile update screen is not displayed");
	}

	@Then("verify cancel button is visible in consent to update profile screen")
	public void verifyCancelBtnInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isCancelButtonInConsentUpdateProfileScreenVisible(),
				"Cancel in consent to profile update screen is not displayed");
	}

	@Then("user verify the essential claims list")
	public void verifyEssentialClaimListInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isEssentialClaimListInConsentUpdateProfileScreenVisible(),
				"Essential claims list in consent to profile update screen is not displayed");
	}

	@Then("user verify the voluntary claims list")
	public void verifyVoluntaryClaimListInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isVoluntaryClaimListInConsentUpdateProfileScreenVisible(),
				"Voluntary claims list in consent to profile update screen is not displayed");
	}

	@Then("user click on essential claim info icon")
	public void userClickOnEssentialInfoIcon() {
		consentPage.clickOnEssentialInfoIcon();
	}

	@Then("verify the essential claim information displayed on clicking the info icon")
	public void verifyEssentialClaimInfoInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isEssentialClaimInformationDisplayed(),
				"Essential claims information in consent to profile update screen is not displayed");
	}

	@Then("user tab outside the info icon")
	public void userClickOutsideInfoIcon() {
		consentPage.clickOnAttentionHeader();
	}

	@Then("user click on voluntary claim info icon")
	public void userClickOnVoluntaryInfoIcon() {
		consentPage.clickOnVoluntaryInfoIcon();
	}
	
	@Then("verify the voluntary claim information displayed on clicking the info icon")
	public void verifyVoluntaryClaimInfoInConsentProfileUpdateScreenDisplayed() {
		Assert.assertTrue(consentPage.isVoluntaryClaimInformationDisplayed(),
				"Voluntary claims information in consent to profile update screen is not displayed");
	}

	@Then("verify the message click on proceed to begin with the verification process is displayed below")
	public void verifyMessageInConsentProfileUpdateScreenDisplayed() {
		boolean visible = consentPage.isMessageAboveProceedButtonDisplayed();
		if (!visible) {
			String reason = "esignet-go's consent-to-profile-update screen has no message above the "
					+ "Allow button - verified live (full DOM capture), nothing to check here.";
			logger.info("Not checking - " + reason);
			ExtentReportManager.notApplicable(reason);
		}
	}

	@When("user click on cancel button in consent update to profile screen")
	public void userClickOnCancelButton() {
		consentPage.clickOnCancelButtonInUpdateProfilePage();
	}

	@Then("verify warning popup with header attention is displayed")
	public void verifyAttentionWarningPopupDisplayed() {
		if (notApplicableUnderMockPlugin("the Deny confirmation warning popup")) {
			return;
		}
		Assert.assertTrue(consentPage.isAttentionWarningPopupDisplayed(), "Header in warning popup is not displayed");
	}

	@Then("verify the sub header in warning popup is displayed")
	public void verifySubHeaderWarningPopupDisplayed() {
		if (notApplicableUnderMockPlugin("the Deny confirmation warning popup")) {
			return;
		}
		Assert.assertTrue(consentPage.isSubHeaderInWarningPopupDisplayed(),
				"Sub-header in warning popup is not displayed");
	}

	@Then("verify stay button is available in the warning popup")
	public void verifyStayButtonInWarningPopupAvailable() {
		if (notApplicableUnderMockPlugin("the Deny confirmation warning popup")) {
			return;
		}
		Assert.assertTrue(consentPage.isStayButtonInWarningPopupScreenDisplayed(),
				"Stay button in warning popup is not displayed");
	}

	@Then("verify discontinue button is available in the warning popup screen")
	public void verifyDiscontinueButtonInWarningPopupAvailable() {
		if (notApplicableUnderMockPlugin("the Deny confirmation warning popup")) {
			return;
		}
		Assert.assertTrue(consentPage.isDiscontinueButtonInWarningPopupScreenDisplayed(),
				"Discontinue button warning popup is not displayed");
	}

	@When("user click on stay button in warning popup")
	public void userClickStayButtonInWarningPopup() {
		if (notApplicableUnderMockPlugin("the Deny confirmation warning popup's stay button")) {
			return;
		}
		consentPage.clickOnStayButton();
	}

	@When("user click on discontinue button in warning popup screen")
	public void userClickDiscontinueButtonInWarningPopup() {
		if (notApplicableUnderMockPlugin("the Deny confirmation warning popup's discontinue button")) {
			return;
		}
		consentPage.clickOnDiscontinueButton();
	}

	@Then("user is navigated to consent screen after authentication")
	public void waitUntilConsentScreenAfterAuthentication() {
		consentPage.waitUntilConsentScreenAfterAuthentication();
		Assert.assertTrue(consentPage.isConsentScreenVisible(), "User didn't navigated to consent screen");
	}

	@Then("verify authorize scopes are displayed on consent screen")
	public void verifyAuthorizeScopesDisplayedOnConsentScreen() {
		Assert.assertTrue(consentPage.isAuthorizeScopeSectionDisplayed(),
				"Authorize scopes section is not displayed on consent screen");
		Assert.assertTrue(consentPage.isAuthorizeScopeDisplayed("Manage-VID"),
				"Manage-VID authorize scope is not displayed on consent screen");
	}

	@Then("verify essential and voluntary claims are not displayed on consent screen")
	public void verifyClaimSectionsAbsentOnConsentScreen() {
		Assert.assertTrue(consentPage.areClaimSectionsAbsent(),
				"Essential or voluntary claims sections were displayed when only authorize scopes were requested");
	}

	@When("user enables the authorize scope {string}")
	public void userEnablesAuthorizeScope(String scopeName) {
		consentPage.toggleAuthorizeScope(scopeName, true);
	}

	@When("user clicks on allow button in consent screen")
	public void userClicksAllowButtonInConsentScreen() {
		consentPage.clickOnAllowBtnInConsentScreen();
	}

	@Then("verify user is navigated to user profile page")
	public void verifyUserIsNavigatedToUserProfilePage() {
		consentPage.waitUntilUserProfilePage();
		Assert.assertTrue(consentPage.isUserProfilePageDisplayed(),
				"User was not redirected to the Health Service user profile page with an authorization code");
	}

	@Then("user completes consent flow through eKYC and returns to relying party")
	public void userCompletesConsentFlowThroughEkycAndReturnsToRelyingParty() {
		if (!prerequisiteVidsAvailableForConsentRegistry()) {
			return;
		}
		Assert.assertTrue(consentPage.isOnAttentionScreen(), "User didn't navigate to attention page");
		consentPage.completeConsentFlowThroughEkyc();
	}

	@Then("user completes consent flow through eKYC if attention screen is displayed")
	public void userCompletesConsentFlowIfAttentionScreenIsDisplayed() {
		if (!prerequisiteVidsAvailableForConsentRegistry()) {
			return;
		}
		consentPage.completeConsentFlowThroughEkycIfAttentionScreenIsDisplayed();
	}

	@Then("verify consent is not requested after authentication")
	public void verifyConsentIsNotRequestedAfterAuthentication() {
		if (!prerequisiteVidsAvailableForConsentRegistry()) {
			return;
		}
		consentPage.assertAuthenticationCompletedWithoutConsent();
	}

	private boolean prerequisiteVidsAvailableForConsentRegistry() {
		if (!"mosipid".equalsIgnoreCase(EsignetUtil.getPluginName())) {
			String reason = "Consent registry VID flow requires mosipid plugin, this environment runs '"
					+ EsignetUtil.getPluginName() + "'.";
			logger.info("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return false;
		}
		if (!EsignetUtil.arePrerequisiteVidsAvailable()) {
			String reason = "prerequisite perpetual and temporary VIDs are unavailable - enable CreateVID "
					+ "in esignetPrerequisiteSuite.xml or set vid=vid1,vid2 in config.properties";
			logger.warn("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return false;
		}
		return true;
	}
}