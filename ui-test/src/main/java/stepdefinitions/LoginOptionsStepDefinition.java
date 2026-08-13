package stepdefinitions;

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
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ConsentPage;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.ClaimsUtil;
import utils.EsignetUtil;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;

public class LoginOptionsStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(LoginOptionsStepDefinition.class);
	LoginOptionsPage loginOptionsPage;
	SignUpPage signUpPage;
	SignupFormDynamicFiller formFiller;

	public LoginOptionsStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		signUpPage = new SignUpPage(driver);
		formFiller = new SignupFormDynamicFiller(driver);

	}

	// Uses the raw executeCdpCommand API (HasCdp) instead of the typed DevTools/Network bindings
	// from a specific selenium-devtools-vNN jar: that jar is pinned to one CDP protocol version
	// (v134 here), and Selenium falls back to a no-op CDP implementation - silently doing nothing -
	// when the running Chrome's major version drifts far enough from it. executeCdpCommand talks to
	// whatever Chrome is actually running, so it isn't tied to a pinned protocol version at all.
	private void enableNetworkDomainIfNeeded() {
		if (!networkDomainEnabled) {
			((HasCdp) driver).executeCdpCommand("Network.enable", Map.of());
			networkDomainEnabled = true;
		}
	}

	private boolean networkDomainEnabled;

	@When("user's internet connection is disconnected")
	public void userInternetConnectionIsDisconnected() {
		enableNetworkDomainIfNeeded();
		((HasCdp) driver).executeCdpCommand("Network.emulateNetworkConditions", Map.of("offline", true, "latency", 0,
				"downloadThroughput", 0, "uploadThroughput", 0));
	}

	@When("user's internet connection is restored")
	public void userInternetConnectionIsRestored() {
		enableNetworkDomainIfNeeded();
		((HasCdp) driver).executeCdpCommand("Network.emulateNetworkConditions", Map.of("offline", false, "latency", 0,
				"downloadThroughput", -1, "uploadThroughput", -1));
	}

	@Then("verify the network error screen is displayed")
	public void verifyNetworkErrorScreenIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isNetworkErrorScreenDisplayed(), "Network error screen is not displayed");
	}

	@Then("verify language dropdown is not displayed on the network error screen")
	public void verifyLanguageDropdownNotDisplayedOnNetworkErrorScreen() {
		Assert.assertFalse(loginOptionsPage.isLanguageDropdownDisplayed(),
				"Language dropdown should not be displayed on the network error screen");
	}

	@When("user clicks on try again button on the network error screen")
	public void userClicksOnTryAgainButtonOnNetworkErrorScreen() {
		loginOptionsPage.clickTryAgainOnNetworkErrorScreen();
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

	@When("user clicks on mobile number option")
	public void userClicksOnMobileNumberOption() {
		loginOptionsPage.clickOnMobileNumberOption();
	}

	@Then("verify the postfix for mobile number option is {string}")
	public void verifyPostfixForMobileNumberOption(String expectedPostfix) {
		verifyPostfixForLoginIdOption("mobile", expectedPostfix);
	}

	@Then("verify the postfix for {string} option is {string}")
	public void verifyPostfixForLoginIdOption(String optionId, String expectedPostfix) {
		ClaimsUtil.parseFromUrl(authorizeUrl);
		String actualPostfix = ClaimsUtil.getPostfixForLoginIdOption(optionId);
		Assert.assertEquals(actualPostfix, expectedPostfix,
				"Postfix for the '" + optionId + "' login-id option did not match expected value");
	}

	@When("user clicks on mobile prefix dropdown button in password login screen")
	public void userClicksOnMobilePrefixDropdownButtonInPasswordLoginScreen() {
		loginOptionsPage.clickOnPasswordScreenMobilePrefixDropdownButton();
	}

	/**
	 * Types a string longer than the effective maxLength (per the config's
	 * prefix-over-outer precedence, mirroring InputWithPrefix.js exactly) and
	 * confirms the field actually enforces that limit: extra characters are
	 * rejected when a limit applies, or all characters are accepted when
	 * neither level configures one.
	 */
	@Then("verify the maxLength precedence is honored for mobile number with {string} prefix")
	public void verifyMaxLengthPrecedenceForMobileNumberWithPrefix(String prefixLabel) {
		ClaimsUtil.parseFromUrl(authorizeUrl);
		String effectiveMaxLength = ClaimsUtil.getEffectiveMaxLength("mobile", prefixLabel);

		int typedLength = (effectiveMaxLength != null ? Integer.parseInt(effectiveMaxLength) : 10) + 5;
		String digitsToType = "1".repeat(typedLength);

		loginOptionsPage.typeIntoMobileNumberFieldOnPasswordScreen(digitsToType);
		String actualValue = loginOptionsPage.getMobileNumberFieldValueOnPasswordScreen();

		int expectedAcceptedLength = effectiveMaxLength != null ? Integer.parseInt(effectiveMaxLength) : typedLength;
		Assert.assertEquals(actualValue.length(), expectedAcceptedLength,
				"Mobile number field did not enforce the expected effective maxLength (prefix='" + prefixLabel
						+ "', effective maxLength=" + effectiveMaxLength + "): typed " + typedLength
						+ " characters but field held '" + actualValue + "'");
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

	@When("user clicks on back button in authentication screen page")
	public void userClicksOnBackButtonInAuthenticationScreen() {
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

	@Then("verify login header is displayed in authentication screen")
	public void verifyLoginHeaderIsDisplayedInAuthenticationScreen() {
		Assert.assertTrue(loginOptionsPage.isLoginHeaderIsDisplayed(),
				"Login header is not displayed");
	}

	@Then("verify login sub header is displayed in authentication screen")
	public void verifyLoginSubHeaderIsDisplayedInAuthenticationScreen() {
		Assert.assertTrue(loginOptionsPage.isLoginSubHeaderIsDisplayed(),
				"Login sub header is not displayed");
	}

	@Then("verify select preferred id header is displayed in authentication screen")
	public void verifySelectPreferredIdHeaderIsDisplayedInAuthenticationScreen() {
		Assert.assertTrue(loginOptionsPage.isSelectPreferredIdHeaderIsDisplayed(),
				"Select preferred id header is not displayed");
	}

	@Then("verify login header is displayed in verification screen")
	public void verifyLoginHeaderIsDisplayedInVerificationScreen() {
		Assert.assertTrue(loginOptionsPage.isLoginHeaderIsDisplayed(),
				"Login header is not displayed");
	}

	@Then("verify login sub header is displayed in verification screen")
	public void verifyLoginSubHeaderIsDisplayedInVerificationScreen() {
		Assert.assertTrue(loginOptionsPage.isLoginSubHeaderIsDisplayed(),
				"Login sub header is not displayed");
	}

	@When("user enters valid email into email field")
	public void userEntersValidEmailInEmailField() {
		String email = EsignetUtil.getEmailFromAddIdentity();
		loginOptionsPage.enterEmail(email);
	}

	@Then("user enters invalid mobile number into the mobile number field")
	public void userEntersInvalidMobileNumber() {
		loginOptionsPage.enterMobileNumberInPasswordScreen("123456");
	}

	// Prefer EsignetUtil.getPrerequisiteRegisteredPassword() - sourced from the AddIdentity
	// prerequisite cache the same way phone/email already are, so it's available regardless of
	// scenario order or parallelism. It only covers the mock plugin though (mosipid's AddIdentity
	// prerequisite has no password field), so fall back to RegisteredDetails.registeredPassword,
	// which is only ever set by SignupFormDynamicFiller as a side effect of a signup scenario
	// running earlier in the same JVM (a plain static, not even thread-local - scenarios run in
	// parallel with no guaranteed ordering, so this can still be null for mosipid runs). Skip with a
	// clear reason rather than reaching WebElement.sendKeys(null), which previously blew up with a
	// cryptic "Keys to send should be a not null CharSequence" IllegalArgumentException.
	private String requireRegisteredPassword() {
		String password = EsignetUtil.getPrerequisiteRegisteredPassword();
		if (password == null || password.isBlank()) {
			password = EsignetUtil.RegisteredDetails.getPassword();
		}
		if (password == null || password.isBlank()) {
			throw new org.testng.SkipException(
					"Registered password unavailable - no AddIdentity prerequisite password for this plugin, and no signup scenario has populated one yet");
		}
		return password;
	}

	@Then("user enters valid password into mobile password field")
	public void userEnterPasswordForMobileId() {
		loginOptionsPage.enterPasswordForMobileId(requireRegisteredPassword());
	}

	@Then("clicks on login button with password in authentication screen page")
	public void clicksOnLoginButtonWithPasswordButtonInAuthenticationScreen() {
		loginOptionsPage.clickOnLoginButtonWithPasswordButton();
	}

	@Then("verify password with login button is disabled in authentication screen")
	public void verifyPasswordWithLoginButtonDisabledInAuthenticationScreen() {
		Assert.assertFalse(loginOptionsPage.isPasswordWithLoginButtonEnabled(), "Password with login button is enabled");
	}

	@Then("user enters valid password into vid password field")
	public void userEnterPasswordForVidId() {
		loginOptionsPage.enterPasswordForVidId(requireRegisteredPassword());
	}

	@Then("user enters valid password into email password field")
	public void userEnterPasswordForEmailId() {
		loginOptionsPage.enterPasswordForEmailId(requireRegisteredPassword());
	}

	@Then("clicks on prefix number button in password authentication screen page")
	public void clicksOnPrefixNumberButtonInPasswordAuthenticationScreen() {
		loginOptionsPage.clickOnPrefixNumberFieldButtonInPasswordScreen();
	}

	@When("user enters invalid vid into vid field in password authentication screen page")
	public void userEntersInvalidVidInPasswordScreen() {
		loginOptionsPage.enterVidInPasswordScreen("8957093658024750");
	}

	@When("user enters special characters into vid field in password authentication screen page")
	public void userEntersSpecialCharactersInVidFieldInPasswordScreen() {
		loginOptionsPage.enterVidInPasswordScreen("&*&%#@%)");
	}

	@When("user enters only space into vid field in password authentication screen page")
	public void userEntersOnlySpaceInVidFieldInPasswordScreen() {
		loginOptionsPage.enterVidInPasswordScreen("    ");
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
		Assert.assertTrue(loginOptionsPage.isScanningDevicesMessageDisplayed(),
				"Scanning devices message is not displayed on biometric screen");
	}

	@Then("verify retry scan button is not displayed while scanning devices")
	public void verifyRetryScanButtonIsNotDisplayedWhileScanningDevices() {
		Assert.assertTrue(loginOptionsPage.isRetryScanButtonNotDisplayedWhileScanning(),
				"Retry scan button should not be displayed while scanning devices for the first time");
	}

	@Then("verify device not found message is displayed on biometric screen")
	public void verifyDeviceNotFoundMessageIsDisplayedOnBiometricScreen() {
		Assert.assertTrue(loginOptionsPage.waitForDeviceNotFoundMessageDisplayed(),
				"Device not found message is not displayed on biometric screen");
	}

	@When("user clicks on biometric device scan retry button")
	public void userClicksOnBiometricDeviceScanRetryButton() {
		loginOptionsPage.clickOnBiometricDeviceScanRetryButton();
	}

}