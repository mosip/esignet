package stepdefinitions;

import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.L1OptionPage;
import pages.R1Page;
import pages.S1Page;

public class L1OptionStepDefPage {
	public WebDriver driver;
	BaseTest baseTest;
	L1OptionPage L1OptionPage;
	R1Page R1Page;
	S1Page S1Page;

	public L1OptionStepDefPage(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		L1OptionPage = new L1OptionPage(driver);
		R1Page = new R1Page(driver);
		S1Page = new S1Page(driver);

	}

	@Given("user is on relying Health Party Portal")
	public void userIsOnRelyingHealthPartyPortal() {
		S1Page.isUserOnRelyingPortal();
	}

	@Then("user clicks on signup with unified button")
	public void userClicksOnSignupWithUnifiedButton() {
		S1Page.clickOnSignUpWithUnifiedButton();
	}

	@Then("user is on esignet logo page")
	public void userIsOnEsignetLogoPage() {
		S1Page.verifyEsignetLogoPage();
	}

	@Then("user clicks on signup with unified login hyperlink button")
	public void clickSignupWithUnifiedLoginHyperlinkButton() {
		S1Page.clickSignupWithUnifiedLoginLink();
	}

	@And("user clicks on {string} in the same page")
	public void userClicksOnValidMobileNumber(String mobileNumber) {
		R1Page.clickValidMobileNumber();
	}

	@Then("continue button remains enabled")
	public void continueButtonRemainsEnabled() {
		Assert.assertTrue(R1Page.isContinueButtonEnabled(), "Continue button is not enabled");
	}

	@When("user clicks on continue button")
	public void userClicksOnContinueButton() {
		R1Page.clickPushOnContinueButton();
	}

	@Then("user should be redirected to Enter OTP page")
	public void userShouldBeRedirectedToEnterOtpPage() {
		R1Page.isEnterOtpPageDisplayed();
	}

	@Then("user enters complete 6 digit otp {string}")
	public void userEntersComplete6DigitOtp(String otp) {
		R1Page.enterCompleteOtp(otp);
	}

	@Then("verify button should be enabled")
	public void verifyButtonShouldBeEnabled() {
		Assert.assertTrue(R1Page.isVerifyButtonEnabled(), "Verify button is not enabled");
	}

	@When("user clicks on the verify button")
	public void userClicksOnVerifyButton() {
		R1Page.clickPushOnContinueButton();
	}

	@Then("it will come to Your mobile number has been verified successfully page")
	public void verifyMobileNumberSuccessfullyPage() {
		Assert.assertTrue(R1Page.isSuccessFullScreenDisplayed());
	}

	@When("user clicks on continue button in success page")
	public void userClicksOnContinueButtonInSuccessPage() {
		R1Page.clickContinueButtonInSuccessPage();
	}

	@Then("it will be redirected to setup account page")
	public void itWillBeRedirectedToSetupAccountPage() {
		R1Page.verifySetUpAccountPageIsDisplayed();
	}

	@When("user enters on username which is not editable")
	public void userClicksOnUsernameWhichIsNotEditable() {
		R1Page.clickUsernameNoneditable();
	}

	@Then("user enters on {string} in khmer word")
	public void userEntersOnInKhmerWord(String fullName) {
		R1Page.enterFullNameInKhmer(fullName);
	}

	@When("user enters {string} in the password field")
	public void userEntersPassword(String password) {
		R1Page.enterPassword(password);
	}

	@And("user enters on {string} in the password field")
	public void userEntersOnInThePasswordField(String confirmPasswordValue) {
		R1Page.enterConfirmPassword(confirmPasswordValue);
	}

	@And("user clicks on Agrees terms and condition checkbox in setup-account page")
	public void userClicksOnTermsAndConditionCheckBoxInSetupAccountPage() {
		R1Page.clickTermsAndConditionCheckBoxInSetupAccountPage();
	}

	@Then("continue button remains enabled in setup account page")
	public void continueButtonRemainsEnabledInSetupAccountPage() {
		Assert.assertTrue(R1Page.continueButtonEnabled());
	}

	@Then("user click on continue button in setup account page")
	public void userClickOnContinueButtonInSetupAccountPage() {
		R1Page.clickContinueInSetUpAccountPage();
	}

	@Then("congratulation screen is displayed")
	public void congratulationScreenIsDisplayed() {
		R1Page.isCongratulationsPageDisplayed();
	}

	@Then("user clicks on ok button in congratulation page")
	public void userClicksOnOkButtonInCongratulationPage() {
		R1Page.clickOkButtonInSuccessPage();
	}

	@Then("user is redirected to relying party portal page")
	public void userIsRedirectedToRelyingPartyPortalPage() {
		Assert.assertTrue(S1Page.isUserOnRelyingPortal());
	}

	@And("user clicks on Login with password")
	public void userClicksOnLoginWithPassword() {
		L1OptionPage.clickLoginWithPassword();
	}

	@And("user enters {string} in the field")
	public void userEntersRegisteredMobileNumber(String mobileNumber) {
		L1OptionPage.enterRegisteredMobileNumber(mobileNumber);
	}

	@Then("user enters on {string} in the field")
	public void userEntersRegisteredPassword(String password) {
		L1OptionPage.enterRegisteredPassword(password);
	}

	@And("user clicks on login in the same page")
	public void userClicksOnLoginButton() {
		L1OptionPage.clickLoginButton();
	}

	@Then("it will redirect to Health Portal is requesting access to the following page")
	public void itWillRedirectToHealthPortalIsRequestingAccessToTheFollowingPage() {
		Assert.assertTrue(L1OptionPage.isHealthPortalRequestPageDisplayed(),
				"Health Portal access request page is not displayed");
	}

	@Then("user clicks on allow button in requesting access to the following page")
	public void userClicksOnAllowButtonInRequestingAccessPage() {
		L1OptionPage.clickAllowButtonInRequestAccessPage();
	}

	@Then("it will redirect to attention screen")
	public void it_will_redirect_to_attention_screen() {
		Assert.assertTrue(L1OptionPage.isAttentionPageDisplayed(),
				"Attention page is not displayed after clicking allow button.");
	}

	@Then("user clicks on proceed button in attention page")
	public void user_clicks_on_proceed_button_in_attention_page() {
		L1OptionPage.clickProceedButtonInAttentionPage();
	}

	@Then("it will redirect to Complete your eKYC verification with the below simple steps page")
	public void itShouldRedirectToEkycVerificationPage() {
		Assert.assertTrue(L1OptionPage.isEKYCVerificationPageDisplayed(),
				"User is not redirected to the eKYC verification page");
	}

	@Then("clicks on proceed button in Complete your eKYC verification with the below simple steps page")
	public void clicksOnProceedButtonInKycPage() {
		L1OptionPage.clickProceedButtonInKycPage();
	}

	@Then("it goes to eKYC provider page")
	public void it_goes_to_ekyc_provider_page() {
		L1OptionPage.isEkycProviderPageDisplayed();
	}

	@Then("clicks on mock identity verifier")
	public void clicks_on_mock_identity_verifier() {
		L1OptionPage.clickMockIdentityVerifier();
	}

	@Then("proceed button is enabled in ekyc provider page")
	public void proceed_button_is_enabled_in_ekyc_provider_page() {
		Assert.assertTrue(L1OptionPage.isProceedButtonEnabledInMockPage());
	}

	@Then("user clicks on proceed button in ekyc provider page")
	public void user_clicks_on_proceed_button_in_ekyc_provider_page() {
		L1OptionPage.clickProceedButtonInMockPage();
	}

	@Then("it will redirect to terms and condition page of consent screen")
	public void it_will_redirect_to_terms_and_condition_page_of_consent_screen() {
		L1OptionPage.verifyRedirectToTermsAndConditionPage();
	}

	@Then("user clicks on checkbox in consent screen page")
	public void userClicksOnCheckboxInConsentScreenPage() {
		L1OptionPage.clickConsentCheckBox();
	}

	@Then("proceed button remains enabled in terms and condition page")
	public void proceedButtonRemainsEnabledInTermsAndConditionPage() {
		Assert.assertTrue(L1OptionPage.isProceedButtonEnabledInTcPage(), "Proceed button is not enabled in T&C page");
	}

	@Then("user clicks on proceed button in terms and condition box")
	public void userClicksOnProceedButtonInTermsAndConditionPage() {
		L1OptionPage.clickProceedButtonInTcPage();
	}

	@Then("User should be navigated to the camera page")
	public void user_should_be_navigated_to_the_camera_page() {
		assertTrue(L1OptionPage.isCameraPageDisplayed());
	}

	@Then("user navigates to liveness check complete steps")
	public void waitUntilLivenessCheckCompletesInCameraPage() {
		L1OptionPage.waitUntilLivenessCheckCompletes();
	}

	@And("user verify Please take appropriate action page displayed")
	public void userVerifyPleaseTakeAppropriateActionPageDisplayed() {
		Assert.assertTrue(L1OptionPage.isAppropriateActionPageDisplayed());
	}

	@Then("user clicks on allow button on visible consent screen")
	public void userClicksOnAllowButtonOnVisibleConsentScreen() {
		L1OptionPage.clickAllowButtonOnConsentScreen();
	}

	@Then("user redirected to welcome message of relying portal")
	public void userRedirectedToWelcomeMessageOfRelyingPortal() {
		Assert.assertTrue(L1OptionPage.isWelcomeMessageDisplayed());
	}

}