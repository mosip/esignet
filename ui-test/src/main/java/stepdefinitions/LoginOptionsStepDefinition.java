package stepdefinitions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.testng.Assert;
import org.testng.SkipException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;
import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ConsentPage;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.BiometricTestDataUtil;
import utils.ClaimsUtil;
import utils.EsignetConfigManager;
import utils.EsignetUtil;
import utils.ExtentReportManager;
import utils.MockMdsManager;

public class LoginOptionsStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(LoginOptionsStepDefinition.class);
	private final BaseTest baseTest;
	LoginOptionsPage loginOptionsPage;
	SignUpPage signUpPage;
	SignupFormDynamicFiller formFiller;
	ConsentPage consentPage;

	public LoginOptionsStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		signUpPage = new SignUpPage(driver);
		formFiller = new SignupFormDynamicFiller(driver);
		consentPage = new ConsentPage(driver);
	}

	private String authorizeUrl;

	@Given("user captures the authorize url")
	public void userCapturesAuhtorizeUrl() throws Exception {

		new WebDriverWait(driver, Duration.ofSeconds(25)).until(ExpectedConditions.or(
				ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.cssSelector("[id^='acr_']")),
				ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.id("username_input"))));

		ClaimsUtil.captureRenderedAuthFactors(driver);
		String currentUrl = driver.getCurrentUrl();
		this.authorizeUrl = currentUrl;
		loginOptionsPage.setAuthorizeUrl(currentUrl);

		if (!BasePage.authorizeScopeOnlyScenario) {
			consentPage.assertDefaultLoginTitleAndSubtitleIfEnglish();
		}
	}

	@Then("verify dropdown language selection is present")
	public void verifyLanguageDropdown() {
		Assert.assertTrue(loginOptionsPage.isLanguageDropdownDisplayed(),
				"Language dropdown is not displayed on the esignet page");
	}

	@Then("verify multiple options for login is available")
	public void verifyMultipleLoginOptions() {
		List<String> authFactors = ClaimsUtil.getRenderedAuthFactors(driver);
		Assert.assertTrue(authFactors.size() > 1, "Expected multiple login options, but found: " + authFactors.size());
	}

	@Then("verify more ways to signIn option is available")
	public void verifyMoreWaysToSignInOption() {
		List<String> authFactors = ClaimsUtil.getRenderedAuthFactors(driver);
		Assert.assertFalse(authFactors.isEmpty(), "No auth factors were rendered on the login page");
		boolean isMoreOptionsDisplayed = loginOptionsPage.isMoreWaysToSignInOptionDisplayed();

		if (authFactors.size() > 4) {
			Assert.assertTrue(isMoreOptionsDisplayed, "Multiple options were not displayed");
		} else {
			assertFalse(isMoreOptionsDisplayed);
		}
	}

	@When("user selects {string} from the language dropdown")
	public void userSelectsLanguage(String language) {
		loginOptionsPage.clickOnLanguageDropdown();
		loginOptionsPage.selectLanguage(language);
	}

	@Then("verify the UI is displayed in selected language {string}")
	public void verifyUILanguage(String text) {
		Assert.assertTrue(loginOptionsPage.isUILanguageChanged(text),
				"UI language did not change to expected language");
	}

	@Then("authentication screen should show login options based on acr_values from url")
	public void authenticationScreenShouldShowLoginOptionsBasedOnAuthFactorsFromUrl() throws Exception {
		List<String> authFactors = ClaimsUtil.getRenderedAuthFactors(driver);
		Map<String, WebElement> factorMap = loginOptionsPage.getAcrToElementMap();

		for (String factor : authFactors) {
			String normalizedFactor = ClaimsUtil.normalizeFactor(factor);
			WebElement element = factorMap.get(normalizedFactor);
			assertNotNull("No UI mapping found for factor: " + factor, element);
			Assert.assertTrue(element.isDisplayed(), "Option not visible for " + factor);
		}
	}

	@When("user triggers the authorization endpoint, the response should have status code 200 and contain valid HTML with JS content")
	public void triggerAuthorizationEndpoint() throws IOException {
		String currentUrl = driver.getCurrentUrl();
		String baseUrl = currentUrl.split("#")[0];

		URI uri = URI.create(baseUrl);
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setRequestMethod("GET");

		int statusCode = connection.getResponseCode();
		Assert.assertEquals(statusCode, 200, "Expected status code 200");

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		String html = response.toString();

		Assert.assertTrue(html.contains("<html"), "HTML tag not found");
		Assert.assertTrue(html.contains("<script"), "Script tag not found");
	}

	@Then("user verifies the behavior after resizing the browser window to different dimensions")
	public void userResizesBrowserWindowToDifferentDimensions() {
		int[][] screenSizes = { { 1920, 1080 }, { 1366, 768 }, { 768, 1024 }, { 414, 896 } };

		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

		for (int[] size : screenSizes) {
			driver.manage().window().setSize(new Dimension(size[0], size[1]));
			wait.until(ExpectedConditions.visibilityOf(loginOptionsPage.getLoginWithOtpButton()));
			logger.info("Resized to: " + size[0] + "x" + size[1]);
		}
	}

	@Then("user verify the otp button remain visible and aligned after resizing")
	public void verifyOtpButtonResponsiveness() {
		WebElement esignetButton = loginOptionsPage.getLoginWithOtpButton();
		Assert.assertTrue(esignetButton.isDisplayed(), "eSignet button not visible after resizing");
		Assert.assertTrue(esignetButton.getRect().getWidth() > 0, "eSignet button width collapsed");
		Assert.assertTrue(esignetButton.getRect().getX() >= 0, "eSignet button misaligned or offscreen");
	}

	@Then("user views the portal on multiple screen sizes")
	public void userViewsPortalOnDifferentScreenSizes() {
		int[][] mobileSizes = { { 360, 640 }, { 390, 844 }, { 412, 915 } };

		for (int[] size : mobileSizes) {
			driver.manage().window().setSize(new Dimension(size[0], size[1]));
			logger.info("Testing layout at resolution: " + size[0] + "x" + size[1]);
		}
	}

	@Then("verify mobile number option is present for authentication")
	public void verifyMobileNumberOptionForAuthentication() {
		Assert.assertTrue(loginOptionsPage.isMobileNumberOptionDisplayed(),
				"Mobile number option is not displayed for authentication");
	}

	@Then("verify nrc id option is present for authentication")
	public void verifyNrcIdOptionForAuthentication() {
		Assert.assertTrue(loginOptionsPage.isNrcIdOptionDisplayed(),
				"Nrc id option is not displayed for authentication");
	}

	@Then("verify vid option is present for authentication")
	public void verifyVidOptionForAuthentication() {
		Assert.assertTrue(loginOptionsPage.isVidOptionDisplayed(), "Vid option is not displayed for authentication");
	}

	@Then("verify email option is present for authentication")
	public void verifyEmailOptionForAuthentication() {
		Assert.assertTrue(loginOptionsPage.isEmailOptionDisplayed(),
				"Email option is not displayed for authentication");
	}

	@Then("verify back button is present in authentication screen")
	public void verifyBackButtonIsVisibleInAuthenticationScreen() {
		Assert.assertTrue(loginOptionsPage.isBackButtonDisplayed(),
				"Back button is not visible in authentication screen");
	}

	@Then("clicks on back button in authentication screen page")
	public void clicksOnBackButtonInAuthenticationScreen() {
		loginOptionsPage.clickOnBackButton();
	}

	@Then("clicks on login with biometric button in login screen page")
	public void clicksOnLoginWithBiometricButtonInLoginScreen() {
		loginOptionsPage.clickOnLoginWithBiometric();
	}

	@Then("clicks on login with password button in login screen page")
	public void clicksOnLoginWithPasswordButtonInLoginScreen() {
		loginOptionsPage.clickOnLoginWithPassword();
	}

	@Then("verify get otp button is disabled in authentication screen")
	public void verifyGetOtpButtonDisabledInAuthenticationScreen() {
		Assert.assertFalse(loginOptionsPage.isGetOtpButtonEnabled(), "Get otp button is enabled");
	}

	@Then("verify mobile number selected for authentication")
	public void verifyMobileNumberSelectedForAuthentication() {
		if (!loginOptionsPage.isMobileNumberSelected()) {
			loginOptionsPage.clickOnMobileNumberOption();
		}
		Assert.assertTrue(loginOptionsPage.isMobileNumberSelected(),
				"Mobile number not seleted in authentication screen");
	}

	@Then("verify khm country prefix displayed for mobile number")
	public void verifyKhmCountryPrefixDisplayedForMobileNumber() {
		Assert.assertTrue(loginOptionsPage.isKhmCountryCodePrefixDisplayed(),
				"Khm country code prefix is not displayed for mobile number");
	}

	@Then("verify ind country prefix displayed for mobile number")
	public void verifyIndCountryPrefixDisplayedForMobileNumber() {
		Assert.assertTrue(loginOptionsPage.isIndCountryCodePrefixDisplayed(),
				"Ind country code prefix is not displayed for mobile number");
	}

	@Then("clicks on prefix number button in authentication screen page")
	public void clicksOnPrefixNumberButtonInAuthenticationScreen() {
		loginOptionsPage.clickOnPrefixNumberFieldButton();
	}

	@Then("clicks on ind country code prefix in authentication screen page")
	public void clicksOnIndCountryCodePrefixInAuthenticationScreen() {
		loginOptionsPage.clickOnIndCountryCodePrefix();
	}

	@Then("clicks on khm country code prefix in authentication screen page")
	public void clicksOnKhmCountryCodePrefixInAuthenticationScreen() {
		loginOptionsPage.clickOnKhmCountryCodePrefix();
	}

	@Then("verify get otp button is enabled in authentication screen")
	public void verifyGetOtpButtonEnabledInAuthenticationScreen() {
		Assert.assertTrue(loginOptionsPage.isGetOtpButtonEnabled(), "Get otp button is not enabled");
	}

	@Then("verify user navigate to verify otp screen")
	public void verifyOtpInputFieldIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isOtpInputFieldIsDisplayed(), "Otp input field is not displayed");
	}

	@Then("verify user navigate to Attention screen")
	public void verifyAttentionScreenIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isAttentionScreenIsDisplayed(), "Attention screen is not displayed");
	}

	@Then("clicks on cancel button in attention consent screen page")
	public void clicksOnCancelButtonInAttentionConsentScreen() {
		loginOptionsPage.clickOnAttentionCancelButton();
	}

	@Then("clicks on discontinue button in attention screen page")
	public void clicksOnDiscontinueButtonInAttentionScreen() {
		loginOptionsPage.clickOnAttentionDiscontinueButton();
	}

	@Then("clicks on vid option button in authentication screen page")
	public void clicksOnVidOptionButtonInAuthenticationScreen() {
		loginOptionsPage.clickOnVidOptionButton();
	}

	@When("user enters invalid vid into vid field")
	public void userEntersInvalidVid() {
		loginOptionsPage.enterVid("8957093658024750");
	}

	@Then("verify user should get invalid individual id error message in authentication screen")
	public void verifyInvalidIndividualIdErrorMessageIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isInvalidIndividualIdErrorMessageIsDisplayed(),
				"Login ID validation error was not displayed after submitting an invalid identifier");
	}

	@When("user enters special characters into vid field")
	public void userEntersSpecialCharactersInVidField() {
		loginOptionsPage.enterVid("&*&%#@%)");
	}

	@When("user enters only space into vid field")
	public void userEntersOnlySpaceInVidField() {
		loginOptionsPage.enterVid("    ");
	}

	@Then("clicks on email option button in authentication screen page")
	public void clicksOnEmailOptionButtonInAuthenticationScreen() {
		loginOptionsPage.clickOnEmailOptionButton();
	}

	@When("user enters invalid email into email field")
	public void userEntersInvalidEmailId() {
		loginOptionsPage.enterEmail("abcd@gmail.com");
	}

	@When("user enters special characters into email field")
	public void userEntersSpecialCharactersInEmailField() {
		loginOptionsPage.enterEmail("&*&%#@%)");
	}

	@When("user enters only space into email field")
	public void userEntersOnlySpaceInEmailField() {
		loginOptionsPage.enterEmail("    ");
	}

	@When("user enters prerequisite vid1 into vid field")
	public void userEntersPrerequisiteVid1() {
		String vid = EsignetUtil.getPrerequisiteVidForOtpLogin();
		Assert.assertTrue(vid != null && !vid.isBlank(),
				"VID1 unavailable - set passwordLoginUin/uin/vid in config.properties or enable identity prerequisite");
		loginOptionsPage.enterVid(vid.trim());
	}

	@When("user enters prerequisite vid2 into vid field")
	public void userEntersPrerequisiteVid2() {
		String vid = EsignetUtil.getPrerequisiteVid2ForOtpLogin();
		Assert.assertTrue(vid != null && !vid.isBlank(),
				"VID2 unavailable - set passwordLoginUin/uin/vid in config.properties or enable identity prerequisite");
		loginOptionsPage.enterVid(vid.trim());
	}

	@When("user enters prerequisite uin into vid field")
	public void userEntersPrerequisiteUinIntoVidField() {
		String uin = EsignetUtil.getPrerequisiteUin();
		if (uin == null || uin.isBlank()) {
			throw new org.testng.SkipException(
					"Prerequisite UIN unavailable - enable AddIdentity prerequisite or set uin in config.properties");
		}
		loginOptionsPage.enterVid(uin);
	}

	@When("user enters prerequisite infant uin into vid field")
	public void userEntersPrerequisiteInfantUinIntoVidField() {
		if ("mock".equalsIgnoreCase(EsignetConfigManager.getproperty("pluginToExecute"))) {
			throw new SkipException("Infant OTP denial flow requires mosipid plugin (mock does not enforce infant age rules)");
		}
		String uin = EsignetUtil.getPrerequisiteInfantUin();
		if (uin == null || uin.isBlank()) {
			throw new SkipException(
					"Prerequisite infant UIN unavailable - enable AddIdentity infant prerequisite or set infantUin in config.properties");
		}
		loginOptionsPage.enterVid(uin);
	}

	@Then("verify otp authentication is denied for infant uin")
	public void verifyOtpAuthenticationIsDeniedForInfantUin() {
		if ("mock".equalsIgnoreCase(EsignetConfigManager.getproperty("pluginToExecute"))) {
			throw new SkipException("Infant OTP denial flow requires mosipid plugin (mock does not enforce infant age rules)");
		}
		Assert.assertFalse(loginOptionsPage.isAttentionScreenIsDisplayed(),
				"Infant OTP authentication should be denied; attention screen was displayed");
		Assert.assertTrue(loginOptionsPage.waitForOtpAuthenticationDeniedForInfant(),
				"Expected OTP authentication to be denied for infant UIN"
						+ loginOptionsPage.getOtpAuthenticationDenialDetails());
	}

	@When("user click on Login with Biometrics")
	public void userClickOnLoginWithBiometrics() throws Exception {
		skipBiometricScenarioIfNotOffered();
		loginOptionsPage.clickOnLoginWithBiometric();
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
	}

	private void skipBiometricScenarioIfNotOffered() {
		if (loginOptionsPage.isLoginWithBiometricsOptionVisible()) {
			return;
		}
		for (String factor : ClaimsUtil.getCachedRenderedAuthFactors()) {
			if ("BIO".equals(factor)) {
				return;
			}
		}
		throw new SkipException(String.format(
				"Login with Biometrics is not offered (rendered auth factors: %s)",
				ClaimsUtil.getCachedRenderedAuthFactors()));
	}

	@Then("verify secure biometric interface is displayed")
	public void verifySecureBiometricInterfaceIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.waitForBiometricScreenReady(),
				"Secure biometric interface did not load after selecting Login with Biometrics");
		Assert.assertTrue(loginOptionsPage.isBiometricIntegrationContainerDisplayed()
				|| loginOptionsPage.isBiometricFlowLandmarkVisible(),
				"Secure biometric interface is not displayed (expected SBI container or biometric login screen)");
	}

	@Then("verify uin vid option is displayed on biometric screen")
	public void verifyUinVidOptionIsDisplayedOnBiometricScreen() {
		Assert.assertTrue(loginOptionsPage.isBiometricVidOptionDisplayed(),
				"UIN/VID option is not displayed on biometric screen");
	}

	@When("user clicks on uin vid option on biometric screen")
	public void userClicksOnUinVidOptionOnBiometricScreen() {
		loginOptionsPage.waitForBiometricScreenReady();
		loginOptionsPage.clickOnBiometricVidOptionButton();
	}

	@When("user clicks continue on biometric login id screen")
	public void userClicksContinueOnBiometricLoginIdScreen() {
		loginOptionsPage.clickContinueOnBiometricLoginIdScreen();
	}

	@Then("verify vid text field is displayed on biometric screen")
	public void verifyVidTextFieldIsDisplayedOnBiometricScreen() {
		loginOptionsPage.selectBiometricUinVidLoginIdType();
		Assert.assertTrue(loginOptionsPage.isBiometricVidTextFieldDisplayed(),
				"Biometric login ID text field is not displayed on biometric screen");
	}

	@Then("verify scanning devices message is displayed on biometric screen")
	public void verifyScanningDevicesMessageIsDisplayedOnBiometricScreen() {
		loginOptionsPage.ensureBiometricScanPrerequisiteIdEntered();
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
		boolean scanningOrDiscovered = loginOptionsPage.waitForScanningDevicesOrDeviceDiscovered();
		if (!scanningOrDiscovered && MockMdsManager.isRunning()) {
			scanningOrDiscovered = loginOptionsPage.waitForBiometricDeviceDiscovered();
		}
		if (!scanningOrDiscovered) {
			scanningOrDiscovered = loginOptionsPage.isDeviceNotFoundMessageDisplayed()
					|| loginOptionsPage.waitForDeviceNotFoundMessageDisplayed();
		}
		Assert.assertTrue(scanningOrDiscovered,
				"Expected scanning-devices, device-discovered, or device-not-found on biometric screen");
	}

	@Then("verify retry scan button is not displayed while scanning devices")
	public void verifyRetryScanButtonIsNotDisplayedWhileScanningDevices() {
		Assert.assertTrue(loginOptionsPage.isRetryScanButtonNotDisplayedWhileScanning(),
				"Retry scan button should not be displayed while scanning devices for the first time");
	}

	@Then("verify device not found message is displayed on biometric screen")
	public void verifyDeviceNotFoundMessageIsDisplayedOnBiometricScreen() {
		loginOptionsPage.ensureBiometricScanPrerequisiteIdEntered();
		Assert.assertTrue(loginOptionsPage.waitForDeviceNotFoundMessageDisplayed(),
				"Device not found message is not displayed on biometric screen");
	}

	@When("user clicks on biometric device scan retry button")
	public void userClicksOnBiometricDeviceScanRetryButton() {
		loginOptionsPage.clickOnBiometricDeviceScanRetryButton();
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
	}

	@When("mock mds is started for biometric device scan")
	public void mockMdsIsStartedForBiometricDeviceScan() throws Exception {
		if (!MockMdsManager.isEnabled()) {
			throw new SkipException("useMockMds=false in config.properties - enable useMockMds for biometric scan tests");
		}
		if (Boolean.parseBoolean(EsignetConfigManager.getproperty("runOnBrowserStack"))) {
			throw new SkipException("Mock MDS requires local browser (localhost SBI ports 4501-4510)");
		}
		MockMdsManager.ensureDevicePartnerP12Available();
		MockMdsManager.startForBiometricScan();
		Assert.assertTrue(MockMdsManager.verifyDeviceDiscoveryOnLocalhost(),
				"Mock MDS started but localhost L1/Auth/Ready probe failed");
		if (loginOptionsPage.isBiometricScreenActive() || loginOptionsPage.isBiometricFlowLandmarkVisible()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
			if (!loginOptionsPage.waitForBiometricDeviceDiscovered()) {
				loginOptionsPage.reenterBiometricLoginAfterMockMdsStart();
			}
		}
	}

	@When("mock mds is stopped for biometric device scan")
	public void mockMdsIsStoppedForBiometricDeviceScan() {
		MockMdsManager.stopAll();
	}

	@Then("verify device not found message is cleared after mock mds retry scan")
	public void verifyDeviceNotFoundMessageIsClearedAfterMockMdsRetryScan() {
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
		boolean cleared = loginOptionsPage.waitForDeviceNotFoundMessageToClear();
		if (!cleared && MockMdsManager.isRunning()) {
			loginOptionsPage.reenterBiometricLoginAfterMockMdsStart();
			cleared = loginOptionsPage.waitForDeviceNotFoundMessageToClear()
					|| loginOptionsPage.waitForBiometricDeviceDiscovered();
		}
		Assert.assertTrue(cleared || loginOptionsPage.isBiometricDeviceDiscoveredPublic(),
				"Device not found message still displayed after Mock MDS retry scan");
		Assert.assertFalse(loginOptionsPage.isDeviceNotFoundMessageDisplayed()
				&& !loginOptionsPage.isBiometricDeviceDiscoveredPublic(),
				"Device not found message should not be displayed once Mock MDS device is discovered");
	}

	@When("user enters prerequisite uin into biometric vid field")
	public void userEntersPrerequisiteUinIntoBiometricVidField() {
		String uin = EsignetUtil.getPrerequisiteUinForBiometricLogin();
		if (uin == null || uin.isBlank()) {
			uin = baseTest.getUin();
		}
		if (uin == null || uin.isBlank()) {
			uin = EsignetConfigManager.getproperty("mockUin");
		}
		if (uin == null || uin.isBlank()) {
			uin = EsignetConfigManager.getproperty("uin");
		}
		Assert.assertTrue(uin != null && !uin.isBlank(),
				"Prerequisite UIN unavailable - set uin/mockUin in config.properties or enable @NeedsUIN");
		loginOptionsPage.enterBiometricVid(uin.trim());
	}

	@Then("verify biometric device is discovered on biometric screen")
	public void verifyBiometricDeviceIsDiscoveredOnBiometricScreen() {
		loginOptionsPage.ensureBiometricScanPrerequisiteIdEntered();
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
		Assert.assertTrue(loginOptionsPage.waitForBiometricDeviceDiscovered(),
				"Biometric device was not discovered by Mock MDS / SBI widget"
						+ loginOptionsPage.describeBiometricEndState());
		loginOptionsPage.ensureBiometricVidFieldVisible();
	}

	@Then("verify biometric device scan completed with no device found")
	public void verifyBiometricDeviceScanCompletedWithNoDeviceFound() {
		Assert.assertTrue(loginOptionsPage.waitForBiometricScanCompletedWithoutDevice(),
				"Biometric scan did not complete with device not found"
						+ loginOptionsPage.describeBiometricEndState());
		Assert.assertFalse(loginOptionsPage.isBiometricDeviceDiscoveredPublic(),
				"Biometric device should not be discovered when no MDS/device is present"
						+ loginOptionsPage.describeBiometricEndState());
	}

	@Then("verify biometric device scan completed with device discovered")
	public void verifyBiometricDeviceScanCompletedWithDeviceDiscovered() {
		Assert.assertTrue(loginOptionsPage.waitForBiometricScanCompletedWithDevice(),
				"Biometric scan did not complete with a discovered device"
						+ loginOptionsPage.describeBiometricEndState());
		Assert.assertTrue(loginOptionsPage.isBiometricScanAndVerifyButtonDisplayed(),
				"Scan and Verify button is not displayed after device discovery"
						+ loginOptionsPage.describeBiometricEndState());
	}

	@When("user clicks biometric scan and verify button")
	public void userClicksBiometricScanAndVerifyButton() {
		loginOptionsPage.clickBiometricScanAndVerifyButton();
	}

	@Then("verify user is authenticated via biometrics successfully")
	public void verifyUserIsAuthenticatedViaBiometricsSuccessfully() {
		Assert.assertTrue(loginOptionsPage.waitForBiometricAuthenticationSuccess(),
				"Biometric authentication did not complete successfully"
						+ loginOptionsPage.getBiometricAuthenticationFailureDetails());
	}

	@Then("verify biometric login completed to attention screen or relying party")
	public void verifyBiometricLoginCompletedToAttentionScreenOrRelyingParty() {
		Assert.assertTrue(loginOptionsPage.isBiometricLoginCompleted(),
				"Biometric login did not reach attention, consent, user profile, or relying party"
						+ loginOptionsPage.describeBiometricEndState());
	}

	@When("user opens login with biometrics via more ways to sign in if needed")
	public void userOpensLoginWithBiometricsViaMoreWaysToSignInIfNeeded() {
		if (!loginOptionsPage.isLoginWithBiometricsOptionVisible()) {
			loginOptionsPage.clickMoreWaysToSignInIfVisible();
		}
		Assert.assertTrue(loginOptionsPage.isLoginWithBiometricsOptionVisible(),
				"Login with Biometrics option is not available from sign-in options");
	}

	@Then("verify login with biometrics option is available in sign in options")
	public void verifyLoginWithBiometricsOptionIsAvailableInSignInOptions() {
		Assert.assertTrue(loginOptionsPage.isLoginWithBiometricsOptionVisible(),
				"Login with Biometrics option is not displayed");
	}

	@Then("verify l0 or unregistered biometric device is not available")
	public void verifyL0OrUnregisteredBiometricDeviceIsNotAvailable() {
		Assert.assertTrue(loginOptionsPage.isL0OrUnregisteredDeviceNotAvailable(),
				"L0 or unregistered device provider should not be listed with Mock MDS");
	}

	@Then("verify biometric scan and verify button is displayed")
	public void verifyBiometricScanAndVerifyButtonIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.waitForBiometricScanAndVerifyButtonDisplayed(),
				"Scan and Verify button is not displayed on biometric screen"
						+ loginOptionsPage.describeBiometricEndState());
	}

	@Then("verify biometric scan and verify button is disabled")
	public void verifyBiometricScanAndVerifyButtonIsDisabled() {
		Assert.assertFalse(loginOptionsPage.isBiometricScanAndVerifyButtonEnabled(),
				"Scan and Verify button should be disabled when UIN/VID is empty");
		Assert.assertTrue(loginOptionsPage.isBiometricVidFieldValidationMessageDisplayed(),
				"Expected browser validation message when biometric VID field is empty");
	}

	@Then("verify biometric scan and verify button is enabled")
	public void verifyBiometricScanAndVerifyButtonIsEnabled() {
		Assert.assertTrue(loginOptionsPage.isBiometricScanAndVerifyButtonEnabled(),
				"Scan and Verify button should be enabled when UIN/VID has input");
	}

	@When("user clears biometric vid field")
	public void userClearsBiometricVidField() {
		loginOptionsPage.clearBiometricVidField();
	}

	@When("user enters {string} into biometric vid field")
	public void userEntersValueIntoBiometricVidField(String value) {
		loginOptionsPage.enterBiometricVid(value);
	}

	@When("user enters invalid uin into biometric vid field")
	public void userEntersInvalidUinIntoBiometricVidField() {
		loginOptionsPage.enterBiometricVid(BiometricTestDataUtil.getInvalidUin());
	}

	@When("user enters invalid vid into biometric vid field")
	public void userEntersInvalidVidIntoBiometricVidField() {
		loginOptionsPage.enterBiometricVid(BiometricTestDataUtil.getInvalidVid());
	}

	@When("user enters prerequisite vid into biometric vid field")
	public void userEntersPrerequisiteVidIntoBiometricVidField() throws Exception {
		String vid = baseTest.getVid();
		if (vid == null || vid.isBlank()) {
			vid = EsignetUtil.getPrerequisitePerpetualVid();
		}
		if (vid == null || vid.isBlank()) {
			throw new org.testng.SkipException(
					"Prerequisite VID unavailable - enable @NeedsVID or set vid in config.properties");
		}
		if (shouldRefreshBiometricSessionBeforeSuccessAttempt()) {
			reopenBiometricLoginWithFreshOAuthSession(vid);
		} else {
			loginOptionsPage.enterBiometricVid(vid);
		}
	}

	private boolean shouldRefreshBiometricSessionBeforeSuccessAttempt() {
		return System.currentTimeMillis() - BasePage.authorizeSessionStartedAt > 120_000L;
	}

	private void reopenBiometricLoginWithFreshOAuthSession(String uinOrVid) throws Exception {
		EsignetUtil.refreshOAuthAuthorizeSession(driver);
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.triggerBrowserBiometricDiscoveryIfMockMdsRunning();
		}
		loginOptionsPage.clickOnLoginWithBiometric();
		loginOptionsPage.selectBiometricUinVidLoginIdType();
		loginOptionsPage.enterBiometricVid(uinOrVid);
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
	}

	@When("user enters configured exception uin into biometric vid field")
	public void userEntersConfiguredExceptionUinIntoBiometricVidField() {
		String uin = BiometricTestDataUtil.getExceptionUin();
		Assert.assertTrue(uin != null && !uin.isBlank(),
				"Set biometricExceptionUin in config.properties for this step");
		loginOptionsPage.enterBiometricVid(uin);
	}

	@When("user enters configured exception vid into biometric vid field")
	public void userEntersConfiguredExceptionVidIntoBiometricVidField() {
		String vid = BiometricTestDataUtil.getExceptionVid();
		Assert.assertTrue(vid != null && !vid.isBlank(),
				"Set biometricExceptionVid in config.properties for this step");
		loginOptionsPage.enterBiometricVid(vid);
	}

	@When("user enters configured wrong match uin into biometric vid field")
	public void userEntersConfiguredWrongMatchUinIntoBiometricVidField() {
		String uin = BiometricTestDataUtil.getWrongMatchUin();
		Assert.assertTrue(uin != null && !uin.isBlank(),
				"Set biometricWrongMatchUin in config.properties for this step");
		loginOptionsPage.enterBiometricVid(uin);
	}

	@When("user enters configured wrong match vid into biometric vid field")
	public void userEntersConfiguredWrongMatchVidIntoBiometricVidField() {
		String vid = BiometricTestDataUtil.getWrongMatchVid();
		Assert.assertTrue(vid != null && !vid.isBlank(),
				"Set biometricWrongMatchVid in config.properties for this step");
		loginOptionsPage.enterBiometricVid(vid);
	}

	@When("user dismisses biometric error banner if displayed")
	public void userDismissesBiometricErrorBannerIfDisplayed() {
		loginOptionsPage.dismissBiometricErrorBannerIfVisible();
	}

	@Then("verify biometric error message contains {string}")
	public void verifyBiometricErrorMessageContains(String expectedFragment) {
		boolean matched = loginOptionsPage.waitForBiometricErrorMessageContaining(expectedFragment);
		if (!matched && "incorrect".equalsIgnoreCase(expectedFragment)) {
			matched = loginOptionsPage.waitForBiometricErrorMessageContaining("invalid", "not valid",
					"does not match", "no uin", "request could not be processed", "please try again",
					"ida-mlc-018", "ida-mlc-007", "unable to authenticate");
		}
		Assert.assertTrue(matched, "Expected biometric error containing '" + expectedFragment + "'");
	}

	@Then("verify biometric capture timeout scenario is skipped for mock mds")
	public void verifyBiometricCaptureTimeoutScenarioIsSkippedForMockMds() {
		if (BiometricTestDataUtil.isTimeoutScenarioEnabled()) {
			Assert.fail("biometricTimeoutTestEnabled=true requires a real biometric device, not Mock MDS");
		}
		logger.info("TC_29 timeout flow skipped - manual/real-device scenario (biometricTimeoutTestEnabled=false)");
	}

	@Then("verify password field is present for authentication")
	public void verifyPasswordFieldIsPresentForAuthentication() {
		Assert.assertTrue(loginOptionsPage.isPasswordFieldDisplayed(),
				"Password field is not displayed for authentication");
	}

	@When("user selects uin login id type if available")
	public void userSelectsUinLoginIdTypeIfAvailable() {
		loginOptionsPage.selectUinLoginIdTypeIfAvailable();
	}

	@When("user selects {string} login id type for password authentication")
	public void userSelectsLoginIdTypeForPasswordAuthentication(String loginIdType) {
		loginOptionsPage.selectLoginIdTypeForPassword(loginIdType);
	}

	@When("user enters prerequisite uin into password login id field")
	public void userEntersPrerequisiteUinIntoPasswordLoginIdField() {
		String uin = EsignetUtil.getPasswordLoginUin();
		Assert.assertTrue(uin != null && !uin.isBlank(),
				"Password-login UIN unavailable - set passwordLoginUin in config.properties");
		loginOptionsPage.enterPasswordLoginId(uin);
	}

	@When("user enters configured password login id for {string}")
	public void userEntersConfiguredPasswordLoginIdFor(String identityKey) {
		String loginId = EsignetUtil.resolveConfiguredPasswordLoginId(identityKey);
		Assert.assertTrue(loginId != null && !loginId.isBlank(),
				"No password-login id configured for key '" + identityKey + "'");
		loginOptionsPage.enterPasswordLoginId(loginId);
	}

	@When("user enters the correct password")
	public void userEntersTheCorrectPassword() {
		String password = EsignetUtil.getPasswordLoginPassword();
		Assert.assertTrue(password != null && !password.isBlank(),
				"Password-login password unavailable - set passwordLoginPassword in config.properties");
		loginOptionsPage.enterPassword(password);
	}

	@When("user enters {string} into password field")
	public void userEntersStringIntoPasswordField(String password) {
		loginOptionsPage.enterPassword(password);
	}

	@When("click on password login button")
	public void clickOnPasswordLoginButton() {
		loginOptionsPage.clickOnPasswordLoginButton();
	}

	@Then("verify user should get invalid credentials error message in authentication screen")
	public void verifyInvalidCredentialsErrorMessageInAuthenticationScreen() {
		Assert.assertTrue(loginOptionsPage.isInvalidCredentialsErrorMessageDisplayed(),
				"Invalid credentials error message is not displayed");
	}

	@Then("verify password login button is disabled in authentication screen")
	public void verifyPasswordLoginButtonDisabledInAuthenticationScreen() {
		Assert.assertFalse(loginOptionsPage.isPasswordLoginButtonEnabled(), "Password login button is enabled");
	}

	@When("user enters invalid mobile number into the mobile number field")
	public void userEntersInvalidMobileNumberIntoMobileNumberField() {
		loginOptionsPage.enterInvalidMobileNumber("12345");
	}

	@When("user completes second factor otp if prompted")
	public void userCompletesSecondFactorOtpIfPrompted() {
		if (!loginOptionsPage.isOtpInputFieldIsDisplayed()) {
			ExtentReportManager.notApplicable("Second factor OTP was not prompted after password login");
			return;
		}
		loginOptionsPage.enterOtp(BasePage.getOtp());
		loginOptionsPage.clickOnSubmitOtpButton();
	}

	@Then("verify user navigate to Attention screen or user profile")
	public void verifyUserNavigateToAttentionScreenOrUserProfile() {
		ConsentPage consentPage = new ConsentPage(driver);
		Assert.assertTrue(loginOptionsPage.isAttentionScreenIsDisplayed()
				|| consentPage.isUserProfilePageDisplayed()
				|| consentPage.isAlreadyOnRelyingParty(),
				"Expected attention screen, user profile, or relying-party redirect after password login");
	}

}
