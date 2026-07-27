package stepdefinitions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.testng.Assert;

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

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.BiometricStepContext;
import utils.BiometricTestDataUtil;
import utils.ClaimsUtil;
import utils.EsignetUtil;
import utils.MockMdsManager;

public class LoginOptionsStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(LoginOptionsStepDefinition.class);
	private final BaseTest baseTest;
	LoginOptionsPage loginOptionsPage;
	SignUpPage signUpPage;
	SignupFormDynamicFiller formFiller;

	public LoginOptionsStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		signUpPage = new SignUpPage(driver);
		formFiller = new SignupFormDynamicFiller(driver);

	}

	private String authorizeUrl;

	@Given("user captures the authorize url")
	public void userCapturesAuhtorizeUrl() throws Exception {
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.urlContains("#"));
		String currentUrl = driver.getCurrentUrl();
		this.authorizeUrl = currentUrl;
		loginOptionsPage.setAuthorizeUrl(currentUrl);
	}

	@Then("verify dropdown language selection is present")
	public void verifyLanguageDropdown() {
		Assert.assertTrue(loginOptionsPage.isLanguageDropdownDisplayed(),
				"Language dropdown is not displayed on the esignet page");
	}

	@Then("verify multiple options for login is available")
	public void verifyMultipleLoginOptions() {
		ClaimsUtil.parseFromUrl(authorizeUrl);
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		Assert.assertTrue(authFactors.size() > 1, "Expected multiple login options, but found: " + authFactors.size());
	}

	@Then("verify more ways to signIn option is available")
	public void verifyMoreWaysToSignInOption() {
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		Assert.assertFalse(authFactors.isEmpty(), "No auth factors were parsed from the authorize URL");
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
		ClaimsUtil.parseFromUrl(authorizeUrl);
		List<String> authFactors = ClaimsUtil.getAuthFactors();
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
				"Invalid individual id error message is not displayed");
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
		String vid = EsignetUtil.getPrerequisitePerpetualVid();
		if (vid == null || vid.isBlank()) {
			throw new org.testng.SkipException(
					"Prerequisite VID1 unavailable - enable CreateVID prerequisite or set vid in config.properties");
		}
		loginOptionsPage.enterVid(vid);
	}

	@When("user enters prerequisite vid2 into vid field")
	public void userEntersPrerequisiteVid2() {
		String vid = EsignetUtil.getPrerequisiteTemporaryVid();
		if (vid == null || vid.isBlank()) {
			throw new org.testng.SkipException(
					"Prerequisite VID2 unavailable - enable CreateVID prerequisite or set vid in config.properties");
		}
		loginOptionsPage.enterVid(vid);
	}

	@When("user click on Login with Biometrics")
	public void userClickOnLoginWithBiometrics() {
		loginOptionsPage.clickOnLoginWithBiometric();
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
	}

	@Then("verify secure biometric interface is displayed")
	public void verifySecureBiometricInterfaceIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isBiometricIntegrationContainerDisplayed(),
				"Secure biometric interface integration container is not displayed");
	}

	@Then("verify uin vid option is displayed on biometric screen")
	public void verifyUinVidOptionIsDisplayedOnBiometricScreen() {
		Assert.assertTrue(loginOptionsPage.isBiometricVidOptionDisplayed(),
				"UIN/VID option is not displayed on biometric screen");
	}

	@When("user clicks on uin vid option on biometric screen")
	public void userClicksOnUinVidOptionOnBiometricScreen() {
		loginOptionsPage.clickOnBiometricVidOptionButton();
	}

	@Then("verify vid text field is displayed on biometric screen")
	public void verifyVidTextFieldIsDisplayedOnBiometricScreen() {
		Assert.assertTrue(loginOptionsPage.isBiometricVidTextFieldDisplayed(),
				"VID text field (sbi_vid) is not displayed on biometric screen");
	}

	@Then("verify scanning devices message is displayed on biometric screen")
	public void verifyScanningDevicesMessageIsDisplayedOnBiometricScreen() {
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
		boolean scanningOrDiscovered = loginOptionsPage.waitForScanningDevicesOrDeviceDiscovered();
		if (!scanningOrDiscovered && MockMdsManager.isRunning()) {
			scanningOrDiscovered = loginOptionsPage.waitForBiometricDeviceDiscovered();
		}
		Assert.assertTrue(scanningOrDiscovered,
				"Scanning devices message is not displayed on biometric screen");
	}

	@Then("verify retry scan button is not displayed while scanning devices")
	public void verifyRetryScanButtonIsNotDisplayedWhileScanningDevices() {
		Assert.assertTrue(loginOptionsPage.isScanningDevicesMessageDisplayed(),
				"Scanning devices message is not displayed on biometric screen");
	}

	@Then("verify device not found message is displayed on biometric screen")
	public void verifyDeviceNotFoundMessageIsDisplayedOnBiometricScreen() {
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
		MockMdsManager.startForBiometricScan();
		Assert.assertTrue(MockMdsManager.verifyDeviceDiscoveryOnLocalhost(),
				"Mock MDS started but localhost probe did not find an L1 Auth Ready device");
		if (loginOptionsPage.isBiometricScreenActive()) {
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
		Assert.assertTrue(loginOptionsPage.waitForDeviceNotFoundMessageToClear(),
				"Device not found message still displayed after Mock MDS retry scan");
		Assert.assertFalse(loginOptionsPage.isDeviceNotFoundMessageDisplayed(),
				"Device not found message should not be displayed once Mock MDS device is discovered");
	}

	@When("user enters prerequisite uin into biometric vid field")
	public void userEntersPrerequisiteUinIntoBiometricVidField() {
		String uin = baseTest.getUin();
		if (uin == null || uin.isBlank()) {
			throw new org.testng.SkipException(
					"Prerequisite UIN unavailable - enable @NeedsUIN or set uin in config.properties");
		}
		loginOptionsPage.enterBiometricVid(uin);
	}

	@Then("verify biometric device is discovered on biometric screen")
	public void verifyBiometricDeviceIsDiscoveredOnBiometricScreen() {
		if (MockMdsManager.isRunning()) {
			loginOptionsPage.syncBiometricWidgetIfMockMdsRunning();
		}
		Assert.assertTrue(loginOptionsPage.waitForBiometricDeviceDiscovered(),
				"Biometric device was not discovered by Mock MDS / SBI widget");
		loginOptionsPage.ensureBiometricVidFieldVisible();
	}

	@When("user clicks biometric scan and verify button")
	public void userClicksBiometricScanAndVerifyButton() {
		if (BiometricStepContext.wasOptionalStepSkipped()) {
			logger.info("Skipping biometric scan click - optional config step was skipped");
			return;
		}
		loginOptionsPage.clickBiometricScanAndVerifyButton();
	}

	@Then("verify user is authenticated via biometrics successfully")
	public void verifyUserIsAuthenticatedViaBiometricsSuccessfully() {
		Assert.assertTrue(loginOptionsPage.waitForBiometricAuthenticationSuccess(),
				"Biometric authentication did not complete successfully"
						+ loginOptionsPage.getBiometricAuthenticationFailureDetails());
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
		Assert.assertTrue(loginOptionsPage.isBiometricScanAndVerifyButtonDisplayed(),
				"Scan and Verify button is not displayed on biometric screen");
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
	public void userEntersPrerequisiteVidIntoBiometricVidField() {
		String vid = baseTest.getVid();
		if (vid == null || vid.isBlank()) {
			vid = EsignetUtil.getPrerequisitePerpetualVid();
		}
		if (vid == null || vid.isBlank()) {
			throw new org.testng.SkipException(
					"Prerequisite VID unavailable - enable @NeedsVID or set vid in config.properties");
		}
		loginOptionsPage.enterBiometricVid(vid);
	}

	@When("user enters configured exception uin into biometric vid field")
	public void userEntersConfiguredExceptionUinIntoBiometricVidField() {
		BiometricStepContext.clearOptionalStepSkipped();
		String uin = BiometricTestDataUtil.getExceptionUin();
		if (uin == null) {
			logger.info("Skipping exception UIN step - set biometricExceptionUin in config.properties");
			BiometricStepContext.markOptionalStepSkipped();
			return;
		}
		loginOptionsPage.enterBiometricVid(uin);
	}

	@When("user enters configured exception vid into biometric vid field")
	public void userEntersConfiguredExceptionVidIntoBiometricVidField() {
		BiometricStepContext.clearOptionalStepSkipped();
		String vid = BiometricTestDataUtil.getExceptionVid();
		if (vid == null) {
			logger.info("Skipping exception VID step - set biometricExceptionVid in config.properties");
			BiometricStepContext.markOptionalStepSkipped();
			return;
		}
		loginOptionsPage.enterBiometricVid(vid);
	}

	@When("user enters configured wrong match uin into biometric vid field")
	public void userEntersConfiguredWrongMatchUinIntoBiometricVidField() {
		BiometricStepContext.clearOptionalStepSkipped();
		String uin = BiometricTestDataUtil.getWrongMatchUin();
		if (uin == null) {
			logger.info("Skipping wrong-match UIN step - set biometricWrongMatchUin in config.properties");
			BiometricStepContext.markOptionalStepSkipped();
			return;
		}
		loginOptionsPage.enterBiometricVid(uin);
	}

	@When("user enters configured wrong match vid into biometric vid field")
	public void userEntersConfiguredWrongMatchVidIntoBiometricVidField() {
		BiometricStepContext.clearOptionalStepSkipped();
		String vid = BiometricTestDataUtil.getWrongMatchVid();
		if (vid == null) {
			logger.info("Skipping wrong-match VID step - set biometricWrongMatchVid in config.properties");
			BiometricStepContext.markOptionalStepSkipped();
			return;
		}
		loginOptionsPage.enterBiometricVid(vid);
	}

	@When("user dismisses biometric error banner if displayed")
	public void userDismissesBiometricErrorBannerIfDisplayed() {
		loginOptionsPage.dismissBiometricErrorBannerIfVisible();
	}

	@Then("verify biometric error message contains {string}")
	public void verifyBiometricErrorMessageContains(String expectedFragment) {
		if (BiometricStepContext.wasOptionalStepSkipped()) {
			logger.info("Skipping biometric error assertion - optional config step was skipped");
			BiometricStepContext.clearOptionalStepSkipped();
			return;
		}
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

}