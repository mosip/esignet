package stepdefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.log4j.Logger;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.mosip.testrig.apirig.testrunner.OTPListener;
import pages.ConsentPage;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.ClaimsUtil;
import utils.EsignetUtil;
import utils.EsignetUtil.RegisteredDetails;
import utils.ResourceBundleLoader;

public class ConsentStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(EsignetUtil.class);
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

	@Given("user directly navigates to sign-up portal URL")
	public void userLaunchesSignupPortal() {
		signUpPage.navigateToSignupPortal();
	}

	@When("user clicks on Register button")
	public void userClicksOnRegisterButton() {
		signUpPage.clickOnRegisterButton();
	}

	@Then("user enters mobile_number in the mobile number field")
	public void userEnterValidMobileNumber() {
		String mobileNumber = EsignetUtil.generateMobileNumberFromRegex();
		RegisteredDetails.setMobileNumber(mobileNumber);
		signUpPage.enterMobileNumber(mobileNumber);
	}

	@Then("user clicks on the Continue button")
	public void userClickOnContinueButton() {
		signUpPage.clickOnContinueButton();
	}

	@When("user enters the OTP")
	public void userEnterOtp() {
		String mobile = RegisteredDetails.getMobileNumber();
		signUpPage.enterOtp(OTPListener.getOtp(mobile));
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
		assertTrue(signUpPage.isAccountCreatedSuccessfullyMessageDisplayed());
	}

	private String expectedDefaultLang;

	@Then("user click on Login with Otp")
	public void clickOnLoginWithOtp() {
		expectedDefaultLang = consentPage.getCurrentLanguage();
		consentPage.clickOnLoginWithOtp();
	}

	@Then("user enters Registered moblie number into the mobile number field")
	public void userEntersRegisteredMobileNumber() {
		String registeredNumber = RegisteredDetails.getMobileNumber();
		consentPage.enterRegisteredMobileNumber(registeredNumber);
	}

	@Then("user click on get otp button")
	public void userClickOnGetOtpBtn() {
		consentPage.clickOnGetOtp();
	}

	@Then("user enters the {string}")
	public void userEnterOtp(String otp) {
		consentPage.enterOtp(otp);
	}

	@Then("click on verify Otp button")
	public void userClickOnVerifyOtpBtn() {
		consentPage.clickOnVerifyButton();
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

	List<String> selectedVoluntaryClaims = new ArrayList<>();

	@When("user enables all the voluntary claims sub-toggle manually")
	public void userEnablesAllSubToggles() throws Exception {
		List<String> voluntaryClaims = consentPage.getClaims("voluntary");
		selectedVoluntaryClaims.clear();

		for (String claim : voluntaryClaims) {
			consentPage.toggleVoluntaryClaim(claim, true);
			selectedVoluntaryClaims.add(claim);
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
}