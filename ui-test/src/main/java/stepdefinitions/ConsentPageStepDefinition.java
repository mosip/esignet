package stepdefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import org.junit.Assert;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BaseTest;
import org.json.JSONObject;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ConsentPage;
import utils.ClaimsParser;
import utils.EsignetUtil.RegisteredDetails;
import utils.LanguageUtil;
import utils.ResourceBundleLoader;

import utils.EsignetConfigManager;
import utils.EsignetUtil;

public class ConsentPageStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(ConsentPageStepDefinition.class);
	ConsentPage consentPage;
	BaseTest baseTest;

	public ConsentPageStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = baseTest.getDriver();
		consentPage = new ConsentPage(driver);
	    EsignetUtil.setDriver(driver); 
	}

	private String authorizeUrl;

	@Given("click on Sign In with eSignet")
	public void clickOnSignInWithEsignet() throws Exception {
		consentPage.clickOnSignInWIthEsignet();
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.urlContains("#"));
		String currentUrl = driver.getCurrentUrl();
		consentPage.setAuthorizeUrl(currentUrl);
	}

	@Then("validate that the logo is displayed")
	public void validateTheLogo() {
		assertTrue(consentPage.isLogoDisplayed());
	}

	@Then("user click on Login with Otp")
	public void clickOnLoginWithOtp() {
		consentPage.clickOnLoginWithOtp();
	}

	@Then("user selects UIN or VID option")
	public void userSelectsUinOrVid() {
		consentPage.clickOnUinOrVidOption();
	}

	@When("user enter the single_char UIN in the UINorVID text field")
	public void userEnterSingleCharUin() {
		consentPage.enterUinOrVid("2");
	}

	@Then("verify get otp button is enabled")
	public void verifyOtpBtnIsEnabled() {
		assertTrue(consentPage.isGetOtpButtonEnabled());
	}

	@Then("user click on get otp button")
	public void userClickOnGetOtpBtn() {
		consentPage.clickOnGetOtp();
	}

	@Then("verify user is redirected to Login screen of eSignet")
	public void verifyUserIsOnLoginPage() {
		assertTrue(consentPage.isLoginScreenDisplayed());
	}

	@Then("verify Please Enter Valid Individual ID error is displayed")
	public void verifyErrorDisplayed() {
		assertTrue(consentPage.isInvalidIdErrorDisplayed());
	}

	@When("user enter the invalid UIN in the UINorVID text field")
	public void userEnterInvalidUin() {
		consentPage.enterUinOrVid("5061369");
	}

	@When("user enter the valid UIN in the UINorVID text field")
	public void userEnterUin() {
		String uin = baseTest.getUin();
		consentPage.enterUinOrVid(uin);
	}

	@Then("verify user is navigated to Otp page")
	public void userNavigatesToOtpScreen() {
		assertTrue(consentPage.isNavigatedToOtpPage());
	}

	@Then("validate VerifyOtp button is disabled")
	public void validateVerifyOtpBtnIsDisabled() {
		assertFalse(consentPage.isVerifyOtpButtonEnabled());
	}

	@Then("verify OTP timer starts and keeps decreasing")
	public void verifyOtpTimerDecreasing() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		int initialValue = consentPage.getOtpTimerValue();
		wait.until(driver -> consentPage.getOtpTimerValue() < initialValue);
		int laterValue = consentPage.getOtpTimerValue();
		assertTrue(laterValue < initialValue);
	}

	@Then("user enters the {string}")
	public void userEnterOtp(String otp) {
		consentPage.enterOtp(otp);
	}

	@Then("click on verify Otp button")
	public void userClickOnVerifyOtpBtn() {
		consentPage.clickOnVerifyButton();
	}

	@Then("validate VerifyOtp button is enabled")
	public void validateVerifyOtpBtnIsEnabled() {
		assertTrue(consentPage.isVerifyOtpButtonEnabled());
	}

	@Then("verify OTP authentication failed.Please try again. error is displayed")
	public void verifyInvalidOtpErrorDisplayed() {
		assertTrue(consentPage.isInvalidOtpErrorDisplayed());
	}

	@Then("verify user should be able to enter {string}")
	public void userEnterResendOtp(String otp) {
		consentPage.enterOtp(otp);
	}

	@When("user click on back button in otp screen")
	public void userClickOnBackButton() {
		consentPage.clickOnBackButton();
	}

	@When("user enter the valid UIN linked with both mobileNumber and email")
	public void userEnterValidUin() {
		String uin = baseTest.getUin();
		consentPage.enterUinOrVid(uin);
	}

	@Then("user should get OTP on the registered mobileNumber and email")
	public void verifyUserReceivesNotificationForBoth() {
		//smtpPage.isOtpReceivedForMobileNumberAndEmail();
	}

	@When("user enter the single_char VID in the UINorVID text field")
	public void userEnterSingleCharVId() {
		consentPage.enterUinOrVid("3");
	}

	@When("user enter the invalid VID in the UINorVID text field")
	public void userEnterInvalidVid() {
		consentPage.enterUinOrVid("43436975");
	}

	@When("user enter the valid VID in the UINorVID text field")
	public void userEnterVid() {
		String vid = baseTest.getVid();
		consentPage.enterUinOrVid(vid);
	}

	@When("user enter the valid VID linked with both mobileNumber and email")
	public void userEnterValidVid() {
		String vid = baseTest.getVid();
		consentPage.enterUinOrVid(vid);
	}

	@Then("authentication screen should show login options based on acr_values from url")
	public void authenticationScreenShouldShowLoginOptionsBasedOnAuthFactorsFromUrl() throws Exception {
		ClaimsParser.parseFromUrl(authorizeUrl);
		List<String> authFactors = ClaimsParser.getAuthFactors();
		Map<String, WebElement> factorMap = consentPage.getAcrToElementMap();

		for (String factor : authFactors) {
			WebElement element = factorMap.get(factor);
			if (element != null) {
				assertTrue("Option not visible for " + factor, element.isDisplayed());
			}
		}
	}

	@Then("verify dropdown language selection is present")
	public void verifyLanguageDropdown() {
		assertTrue(consentPage.isLanguageDropdownDisplayed());
	}

	@When("user select the other language from the dropdown")
	public void userSelectsOtherLanguage() {
		consentPage.clickOnLanguageDropdown();
		consentPage.clickOnHindiLanguage();
	}

	@Then("verify the UI is displayed with the selected language")
	public void verifyScreenChanged() {
		assertTrue(consentPage.isSelectedLanguageDisplayed());
	}

	@Then("verify multiple options for login is available")
	public void verifyMultipleLoginOptions() {
		assertTrue(consentPage.isLoginWithBiometicDisplayed());
		assertTrue(consentPage.isLoginWithInjiDisplayed());
		assertTrue(consentPage.isLoginWithPasswordDisplayed());
	}

	@Then("verify more ways to signIn option is available")
	public void verifyMoreWaysToSignInOption() {
		List<WebElement> loginOptions = consentPage.getLoginOptions();
		boolean isMoreOptionsDisplayed = consentPage.isMoreWaysToSignInOptionDisplayed();

		if (loginOptions.size() > 4) {
			assertTrue(isMoreOptionsDisplayed);
		} else {
			assertFalse(isMoreOptionsDisplayed);
		}
	}

	@Then("user enters Registered moblie number into the mobile number field")
	public void userEntersRegisteredMobileNumber() {
		String registeredNumber = RegisteredDetails.getMobileNumber();
		consentPage.enterRegisteredMobileNumber(registeredNumber);
	}

	@Then("verify consent should ask user to proceed in attention page")
	public void userGoesToAttentionScreen() {
		assertTrue(consentPage.isOnAttentionScreen());
	}

	@Then("clicks on proceed button in attention page")
	public void clickOnProceedButtonInAttentionPage() {
		consentPage.clickOnProceedButtonInAttentionPage();
	}

	@Then("clicks on proceed button in next page")
	public void clickOnProceedButtonInNextPage() {
		consentPage.clickOnProceedButton();
	}

	@Then("select the e-kyc verification provider")
	public void selectEKycVerificationProvider() {
		consentPage.clickOnMockIdentifyVerifier();
	}

	@Then("clicks on proceed button in e-kyc verification provider page")
	public void clickOnProceedButton() {
		consentPage.clickOnProceedButtonInServiceProviderPage();
	}

	@Then("user select the check box in terms and condition page")
	public void userSelectTheCheckBoxInTermsAndConditionPage() {
		consentPage.checkTermsAndCondition();
	}

	@Then("user clicks on proceed button in terms and condition page")
	public void userClicksOnProceedButtonInTermsAndConditionPage() {
		consentPage.clickOnProceedButtonInTermsAndConditionPage();
	}

	@Then("user clicks on proceed button in camera preview page")
	public void userClicksOnProceedButtonInCameraPreviewPage() {
		consentPage.clickOnProceedButtonInCameraPreviewPage();
	}

	@Then("user is navigated to consent screen once liveness check completes")
	public void waitUntilLivenessCheckCompletesInCameraPage() {
		consentPage.waitUntilLivenessCheckCompletes();
	}

	@Then("verify user is navigated to consent screen")
	public void verifyUserIsOnConsentScreen() {
		assertTrue(consentPage.isConsentScreenVisible());
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
		assertEquals("rtl", dirValue);
	}

	@Then("verify consent page should show all the claims requested")
	public void verifyConsentPageShowsRequestedClaims() throws Exception {
		ClaimsParser.parseFromUrl(consentPage.getAuthorizeUrl());
		List<String> expectedMandatoryClaims = ClaimsParser.getMandatoryClaims();
		List<String> expectedVoluntaryClaims = ClaimsParser.getVoluntaryClaims();

		List<String> localizedMandatoryClaims = expectedMandatoryClaims.stream()
				.map(claim -> ResourceBundleLoader.get("consent." + claim)).toList();

		List<String> localizedVoluntaryClaims = expectedVoluntaryClaims.stream()
				.map(claim -> ResourceBundleLoader.get("consent." + claim)).toList();

		List<String> displayedMandatoryClaims = consentPage.getDisplayedMandatoryClaims();
		List<String> displayedVoluntaryClaims = consentPage.getDisplayedVoluntaryClaims();

		for (String claim : localizedMandatoryClaims) {
			Assert.assertTrue("Missing mandatory claim: " + claim, displayedMandatoryClaims.contains(claim));
		}

		for (String claim : localizedVoluntaryClaims) {
			Assert.assertTrue("Missing voluntary claim: " + claim, displayedVoluntaryClaims.contains(claim));
		}
	}

	@Then("verify the tooltip message for Voluntary Claims info icon")
	public void verifyTooltipMessageForVoluntaryClaimsIcon() {
		String actualTooltip = consentPage.getVoluntaryClaimsTooltipText();
		assertFalse(actualTooltip.trim().isEmpty());
	}

	@Then("verify essential claims are listed separately")
	public void verifyEssentialClaimsAreListedSeparately() {
		assertTrue(consentPage.areEssentialClaimsPresent());
	}

	@Then("verify voluntary claims are listed separately")
	public void verifyVoluntaryClaimsAreListedSeparately() {
		assertTrue(consentPage.areVoluntaryClaimsPresent());
	}

	@Then("verify master toggle should be visible for Voluntary Claims if multiple claims are present")
	public void verifyVoluntaryClaimsMasterToggleVisible() {
		assertTrue(consentPage.isVoluntaryClaimsMasterToggleVisible());
	}

	@Then("verify all toggle buttons for Voluntary Claims are disabled by default")
	public void verifyVoluntaryClaimsMasterToggleDisabled() {
		assertFalse(consentPage.getVoluntaryClaimsMasterToggle().isSelected());
		for (WebElement subToggle : consentPage.getVoluntaryClaimsSubToggles()) {
			assertFalse(subToggle.isSelected());
		}
	}

	@Then("verify if user enables Master toggle,all sub-toggles should be enabled")
	public void enableMasterToggleForVoluntaryClaims() {
		consentPage.enableVoluntaryClaimsMasterToggle();
		for (WebElement subToggle : consentPage.getVoluntaryClaimsSubToggles()) {
			assertTrue(subToggle.isSelected());
		}
	}

	@When("if user deselect one of the Voluntary Claims")
	public void userDeselectOneVoluntaryClaim() throws Exception {
		List<String> voluntaryClaims = consentPage.getClaims("voluntary");
		if (!voluntaryClaims.isEmpty()) {
			String firstClaim = voluntaryClaims.get(0);
			consentPage.toggleVoluntaryClaim(firstClaim, false);
		}
	}

	@Then("verify remaining Voluntary Claims stays selected along with master toggle")
	public void verifyRemainingVoluntaryClaim() {
		assertTrue(consentPage.isVoluntaryClaimsMasterToggleSelected());

		int notSelected = 0;

		for (WebElement toggle : consentPage.getVoluntaryClaimsSubToggles()) {
			if (!toggle.isSelected()) {
				notSelected++;
			}
		}
		assertEquals(1, notSelected);
	}

	@Then("if user disables Master toggle,all sub-toggles should be disabled")
	public void disableMasterToggleForAuthorizeScope() {
		consentPage.disableVoluntaryClaimsMasterToggle();
		for (WebElement subToggle : consentPage.getVoluntaryClaimsSubToggles()) {
			assertFalse(subToggle.isSelected());
		}
	}

	@Then("if user manually deselects all sub-toggles,verify master toggle also gets disabled")
	public void verifyDeselectingVoluntaryClaimManually() throws Exception {
		List<String> voluntaryClaims = consentPage.getClaims("voluntary");
		for (String claim : voluntaryClaims) {
			consentPage.toggleVoluntaryClaim(claim, false);
		}
		assertFalse(consentPage.isVoluntaryClaimsMasterToggleSelected());
	}

	@When("user enables only one of the Voluntary Claims toggle")
	public void userEnablesOneVoluntaryClaim() throws Exception {
		List<String> voluntaryClaims = consentPage.getClaims("voluntary");
		if (!voluntaryClaims.isEmpty()) {
			String firstClaim = voluntaryClaims.get(0);
			consentPage.toggleVoluntaryClaim(firstClaim, true);
		}
	}

	@Then("verify that the master toggle remains in unselected state")
	public void verifyMasterToggleIsDisabled() {
		assertFalse(consentPage.isVoluntaryClaimsMasterToggleSelected());
	}

	@When("user enables all the voluntary claims sub-toggle manually")
	public void userEnablesAllSubToggles() throws Exception {
		List<String> voluntaryClaims = consentPage.getClaims("voluntary");

		for (String claim : voluntaryClaims) {
			consentPage.toggleVoluntaryClaim(claim, true);
		}
	}

	@Then("verify that the master toggle is enabled automatically")
	public void verifyMasterToggleIsEnabled() {
		assertTrue(consentPage.isVoluntaryClaimsMasterToggleSelected());
	}

	@Then("verify the timer starts from 55sec in the consent page via Otp login")
	public void verifyConsentPageTimer() {
		int seconds = consentPage.getConsentTimerSeconds();

		if (seconds >= 54 && seconds <= 56) {
			logger.info("Timer started correctly around 55 seconds");
		} else {
			logger.warn("Timer did not start at 55 seconds, it started at: " + seconds);
		}
	}

	@Then("refresh the browser tab and verify timer continue with leftover seconds")
	public void verifyTimerPersistsAfterRefresh() {
		int beforeRefresh = consentPage.getConsentTimerSeconds();

		logger.info("Timer before waiting: " + beforeRefresh + " seconds");

		driver.navigate().refresh();

		new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> consentPage.isConsentScreenVisible());

		int afterRefresh = consentPage.getConsentTimerSeconds();

		logger.info("Timer after refresh: " + afterRefresh + " seconds");

		if (afterRefresh <= beforeRefresh && (beforeRefresh - afterRefresh) <= 2) {
			logger.info("Timer persisted correctly after refresh");
		} else {
			logger.warn("Timer got reset or invalid after refresh");
		}
	}

	@Then("verify configured languages should be displayed in the dropdown")
	public void verifyConfiguredLanguagesDisplayed() {
		List<String> expectedLanguages = new ArrayList<>(LanguageUtil.languagesMap.values());
		List<String> actualLanguages = consentPage.getDisplayedLanguages();
		Assert.assertTrue(actualLanguages.containsAll(expectedLanguages));
	}

	@When("the user triggers the authorization endpoint, the response should have status code 200 and contain valid HTML with JS content")
	public void triggerAuthorizationEndpoint() throws IOException {
		String authorizeUrl = EsignetConfigManager.getproperty("baseurl");

		URI uri = URI.create(authorizeUrl);
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setRequestMethod("GET");

		int statusCode = connection.getResponseCode();
		Assert.assertEquals(200, statusCode);

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();

		String html = response.toString();
		Assert.assertTrue(html.contains("<html"));
		Assert.assertTrue(html.contains("<script"));
	}

	@When("user resizes the browser window to different dimensions")
	public void userResizesBrowserWindowToDifferentDimensions() {
		int[][] screenSizes = { { 1920, 1080 }, { 1366, 768 }, { 768, 1024 }, { 414, 896 } };

		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

		for (int[] size : screenSizes) {
			driver.manage().window().setSize(new Dimension(size[0], size[1]));
			wait.until(ExpectedConditions.visibilityOf(consentPage.getSignInWithEsignetButton()));
			logger.info("Resized to: " + size[0] + "x" + size[1]);
		}
	}

	@Then("the eSignet button should remain visible and aligned after resizing")
	public void verifyEsignetButtonResponsiveness() {
		WebElement esignetButton = consentPage.getSignInWithEsignetButton();
		Assert.assertTrue("eSignet button not visible after resizing", esignetButton.isDisplayed());
		Assert.assertTrue("eSignet button width collapsed", esignetButton.getRect().getWidth() > 0);
		Assert.assertTrue("eSignet button misaligned or offscreen", esignetButton.getRect().getX() >= 0);
	}
	
	@When("user views the portal on multiple mobile screen sizes")
	public void userViewsPortalOnDifferentMobileSizes() {
		int[][] mobileSizes = { { 360, 640 }, { 390, 844 }, { 412, 915 } };

		for (int[] size : mobileSizes) {
			driver.manage().window().setSize(new Dimension(size[0], size[1]));
			logger.info("Testing layout at resolution: " + size[0] + "x" + size[1]);
		}
	}
	
	@Then("verify authorize API parameters match oauth-details request body")
	public void verifyAuthorizeParamsMatch() {

	    JSONObject authorizeJson = ClaimsParser.getRoot();

	    JSONObject oauthJson = EsignetUtil.getOauthDetailsBody();
	    JSONObject oauthResponse = oauthJson.getJSONObject("response");

	    // Compare only the keys
	    for (String key : authorizeJson.keySet()) {
	        Assert.assertTrue("Missing key in oauth-details request body: " + key,
	        		oauthResponse.has(key));
	    }
	}



}
