package stepdefinitions;

import static org.junit.Assert.assertEquals;
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
import io.mosip.testrig.apirig.testrunner.OTPListener;
import pages.ConsentPage;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.EsignetUtil;
import utils.EsignetUtil.RegisteredDetails;

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
		Assert.assertTrue(signUpPage.isAccountCreatedSuccessfullyMessageDisplayed(),
				"Success message is not displayed");
	}

	private String expectedDefaultLang;

	@Then("user click on Login with Otp")
	public void clickOnLoginWithOtp() {
		expectedDefaultLang = consentPage.getCurrentLanguage();
		consentPage.clickOnLoginWithOtp();
	}

	@Then("user enters Registered mobile number into the mobile number field")
	public void userEntersRegisteredMobileNumber() {
		String registeredNumber = RegisteredDetails.getMobileNumber();
		consentPage.enterRegisteredMobileNumber(registeredNumber);
	}

	@Then("user click on get otp button")
	public void userClickOnGetOtpBtn() {
		consentPage.clickOnGetOtp();
	}

	@Then("user enters the correct otp")
	public void userEnterCorrectOtp() {
		consentPage.enterOtp(BasePage.getOtp());
	}

	@Then("click on verify Otp button")
	public void userClickOnVerifyOtpBtn() {
		consentPage.clickOnVerifyButton();
	}

	@Then("verify consent should ask user to proceed in attention page")
	public void userGoesToAttentionScreen() {
		Assert.assertTrue(consentPage.isOnAttentionScreen(), "User didn't navigated to attention page");
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
		assertEquals("rtl", dirValue);
	}

	@Then("verify the tooltip message for Voluntary Claims info icon")
	public void verifyTooltipMessageForVoluntaryClaimsIcon() {
		String actualTooltip = consentPage.getVoluntaryClaimsTooltipText();
		assertFalse(actualTooltip.trim().isEmpty());
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
		assertFalse(consentPage.getVoluntaryClaimsMasterToggle().isSelected());
		for (WebElement subToggle : consentPage.getVoluntaryClaimsSubToggles()) {
			assertFalse(subToggle.isSelected());
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
		List<String> voluntaryClaims = consentPage.getClaims("voluntary");
		assertFalse("Voluntary claims were not loaded for this scenario", voluntaryClaims.isEmpty());
		if (!voluntaryClaims.isEmpty()) {
			String firstClaim = voluntaryClaims.get(0);
			consentPage.toggleVoluntaryClaim(firstClaim, false);
		}
	}

	@Then("verify remaining Voluntary Claims stays selected along with master toggle")
	public void verifyRemainingVoluntaryClaim() {
		Assert.assertTrue(consentPage.isVoluntaryClaimsMasterToggleSelected(),
				"Voluntary claims master toggle is not selected");

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
		assertFalse("Voluntary claims were not loaded for this scenario", voluntaryClaims.isEmpty());
		for (String claim : voluntaryClaims) {
			consentPage.toggleVoluntaryClaim(claim, false);
		}
		assertFalse(consentPage.isVoluntaryClaimsMasterToggleSelected());
	}

	@When("user enables only one of the Voluntary Claims toggle")
	public void userEnablesOneVoluntaryClaim() throws Exception {
		List<String> voluntaryClaims = consentPage.getClaims("voluntary");
		assertFalse("Voluntary claims were not loaded for this scenario", voluntaryClaims.isEmpty());
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

	@Then("verify the timer starts from 55sec in the consent page via Otp login")
	public void verifyConsentPageTimer() {
		int seconds = consentPage.getConsentTimerSeconds();
		Assert.assertTrue(seconds >= 54 && seconds <= 56, "Timer should start around 55 seconds, but was: " + seconds);
	}

	@Then("refresh the browser tab and verify timer continue with leftover seconds")
	public void verifyTimerPersistsAfterRefresh() {
		int beforeRefresh = consentPage.getConsentTimerSeconds();
		logger.info("Timer before waiting: " + beforeRefresh + " seconds");
		driver.navigate().refresh();
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> consentPage.isConsentScreenVisible());

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
}