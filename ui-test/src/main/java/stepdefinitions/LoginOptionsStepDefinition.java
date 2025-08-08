package stepdefinitions;

import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

import org.openqa.selenium.WebDriver;

import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.LoginOptionsPage;
import pages.RegistrationPage;

public class LoginOptionsStepDefinition {

	public WebDriver driver;
	BaseTest baseTest;
	LoginOptionsPage loginOptionsPage;
	RegistrationPage registrationPage;

	public LoginOptionsStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		registrationPage = new RegistrationPage(driver);
	}

	@Given("click on Sign In with eSignet")
	public void clickOnSignInWithEsignet() {
		loginOptionsPage.clickOnSignInWIthEsignet();
	}

	@Then("validate that the logo is displayed")
	public void validateTheLogo() {
		assertTrue(loginOptionsPage.isLogoDisplayed());
	}

	@Then("user clicks on continue button on success page")
	public void userClicksOnContinueButtonOnSuccessPage() {
		registrationPage.clickOnContinueButtonInSucessScreen();
	}

	@And("user prolonged to registration page")
	public void user_prolonged_to_registration_page() {
		Assert.assertTrue(registrationPage.isProlongedToRegistrationPageVisible(),
				"Registration page container should be visible");
	}

	@And("user click out side on password field")
	public void userClickOutsideOnPasswordField() {
		registrationPage.tabsOutOfField();
	}

	@Then("verify invalid password error should be shown")
	public void verifyInvalidPasswordErrorShouldBeShown() {
		Assert.assertTrue(registrationPage.isInvalidPasswordErrorVisible(), "Invalid password error should be shown");
	}

	@When("user enters {string} into the password field")
	public void enterLongPasswordIntoPasswordField(String Password) {
		registrationPage.enterPassword(Password);
	}

	@Then("system should restrict password input to max allowed")
	public void systemShouldRestrictPasswordInputToMaxAllowed() {
		Assert.assertTrue(registrationPage.isPasswordRestrictionMessageDisplayed(),
				"Password restriction message should be visible");
	}

	@When("user enters {string} into password field for confirm")
	public void user_enters_confirm_password(String confirmPassword) {
		registrationPage.enterConfirmPassword(confirmPassword);
	}

	@And("user clicks on agrees terms condition check-box")
	public void userClicksOnAgreesTermsCheckbox() {
		registrationPage.checkTermsAndConditions();
	}

	@Then("user clicks on continue button on registration page")
	public void userClicksOnContinueButtonOnRegistrationPage() {
		registrationPage.clickOnSetupAccountContinueButton();
	}

	@Then("it will redirect to congratulations on login page")
	public void verifyCongratulationPageIsDisplayed() {
		Assert.assertTrue(registrationPage.isCongratulationScreenDisplayed(), "Congratulations page should be visible");
	}

	@Then("user clicks on login button")
	public void user_clicks_on_login_button() {
		registrationPage.clickOnLoginButtonInSuccessScreen();
	}

	@Then("verify user is redirected to login page of relying portal")
	public void redirectedToLoginUsingEsignetPage() {
		Assert.assertTrue(loginOptionsPage.isLoginEsignetPageDisplayed(),
				"User should be redirected to login using e-Signet page");
	}

	@And("user clicks on login with password button")
	public void user_clicks_on_login_with_password_button() {
		loginOptionsPage.clickOnLoginWithPasswordOption();
	}

	@Then("verify login button is disabled")
	public void verifyLoginButtonIsDisabled() {
		assertFalse(loginOptionsPage.isLoginButtonEnabled());
	}

	@When("user enter {string} into mobile number field")
	public void userEntersMobileNumber(String number) {
		loginOptionsPage.enterRegisteredMobileNumber(number);
	}

	@Then("user enters {string} into password field")
	public void userFillsConfirmPasswordField(String Password) {
		loginOptionsPage.enterPassword(Password);
	}

	@And("user clicks on login button in login page")
	public void userClicksOnLoginBtnInLoginPage() {
		loginOptionsPage.clickOnLoginButton();
	}

	@Then("verify error Please Enter Valid Individual ID. is displayed")
	public void verifyInvalidErrorDisplayed() {
		assertTrue(loginOptionsPage.isInvalidNumberErrorDisplayed());
	}

	@And("user tabout of password field")
	public void userTabOut() {
		loginOptionsPage.userTaboutOfPasswordField();
	}

	@Then("verify error Please Enter Valid Password is displayed")
	public void verifyInvalidPasswordErrorDisplayed() {
		assertTrue(loginOptionsPage.isInvalidPasswordErrorDisplayed());
	}

	@Then("verify error Username or password is not valid. Please enter valid credentials. is displayed")
	public void verifyInvalidUsernameOrPasswordErrorDisplayed() {
		assertTrue(loginOptionsPage.isInvalidUsernameOrPasswordErrorDisplayed());
	}

	@When("user click on the close icon in error message")
	public void userClickOnErrorCloseIcon() {
		loginOptionsPage.clickOnErrorCloseIcon();
	}

	@Then("verify the error message disappears")
	public void verifyErrorMessageIsGone() {
		loginOptionsPage.verifyErrorDisappearsAfterClose();
	}

	@Then("verify error message disappears automatically after 10 seconds")
	public void waitForErrorMessageToDisappear() {
		loginOptionsPage.verifyErrorMessageDisappearsAfterTenSeconds();
	}

	@Then("verify login button is enabled")
	public void verifyLoginButtonIsEnabled() {
		assertTrue(loginOptionsPage.isLoginButtonEnabled());
	}

	@Then("verify consent should ask user to proceed in attention page")
	public void userGoesToAttentionScreen() {
		Assert.assertTrue(loginOptionsPage.isOnAttentionScreen(), "User is not on the attention screen.");
	}

	@And("clicks on proceed button in attention page")
	public void clickOnProceedButtonInAttentionPage() {
		loginOptionsPage.clickOnProceedButtonInAttentionPage();
	}

	@And("clicks on proceed button in next page")
	public void clickOnProceedButtonInNextPage() {
		loginOptionsPage.clickOnProceedButton();
	}

	@Then("select the e-kyc verification provider")
	public void selectEKycVerificationProvider() {
		loginOptionsPage.clickOnMockIdentifyVerifier();
	}

	@And("clicks on proceed button in e-kyc verification provider page")
	public void clickOnProceedButton() {
		loginOptionsPage.clickOnProceedButtonInServiceProviderPage();
	}

	@And("user select the check box in terms and condition page")
	public void userSelectTheCheckBoxInTermsAndConditionPage() {
		loginOptionsPage.checkTermsAndConditions();
	}

	@And("user clicks on proceed button in terms and condition page")
	public void userClicksOnProceedButtonInTermsAndConditionPage() {
		loginOptionsPage.clickOnProceedButtonInTermsAndConditionPage();
	}

	@And("user clicks on proceed button in camera preview page")
	public void userClicksOnProceedButtonInCameraPreviewPage() {
		loginOptionsPage.clickOnProceedButtonInCameraPreviewPage();
	}

	@Then("user is navigated to consent screen once liveness check completes")
	public void waitUntilLivenessCheckCompletesInCameraPage() {
		loginOptionsPage.waitUntilLivenessCheckCompletes();
	}

	@And("verify the header Please take appropriate action in xx:xx is displayed")
	public void verifyConsentHeaderDisplayed() {
		Assert.assertTrue(loginOptionsPage.isConsentSceenDisplayed(), "Consent page should be displayed");
	}

	@And("verify logos of the relying party and e-Signet is displayed")
	public void verifyBothLogosAreDisplayed() {
		Assert.assertTrue(loginOptionsPage.areLogosDisplayed(),
				"Logos of relying party and e-Signet should be visible");
	}

	@And("verify the essential claims with \"i\" icon is displayed")
	public void verifyEssentialIIconIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isEssentialIconDisplayed(),
				"\"i\" icon for essential claims should be visible");
	}

	@And("verify required essential claims is displayed")
	public void verifyEssentialClaimsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isEssentialClaimsDisplayed(), "Essential claims should be displayed");
	}

	@And("verify the voluntary claims with \"i\" icon is displayed")
	public void verifyVoluntaryClaimsIconIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isVoluntaryClaimsIconDisplayed(),
				"\"i\" icon for voluntary claims should be displayed");
	}

	@And("verify list of voluntary claims displayed")
	public void verifyListOfVoluntaryClaimsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isVoluntaryClaimsDisplayed(), "Voluntary claims list is not displayed.");
	}

	@And("verify allow button is visible consent screen")
	public void verifyAllowButtonInConsentScreen() {
		Assert.assertTrue(loginOptionsPage.isAllowButtonInConsentScreenVisible(),
				"Allow button should be visible on Consent screen");
	}

	@And("verify cancel button is visible consent screen")
	public void verifyCancelButtonInConsentScreen() {
		Assert.assertTrue(loginOptionsPage.isCancelButtonInConsentScreenVisible(),
				"Allow button should be visible on Consent screen");
	}

	@Then("verify the tooltip message for Essential Claims info icon")
	public void verifyTooltipMessageForEssentialClaimsIcon() {
		String expectedMessage = "Mandatory user information required by the Service provider";
		String actualTooltip = loginOptionsPage.getEssentialClaimsTooltipText();
		Assert.assertEquals(actualTooltip.trim(), expectedMessage, "Tooltip message does not match");
	}

	@And("verify Phone Number should be listed under Essential Claims")
	public void verifyPhoneNumberListedUnderEssentialClaims() {
		Assert.assertTrue(loginOptionsPage.isPhoneNumberListedUnderEssentialClaims(),
				"Phone Number is not listed under Essential Claims");
	}

	@And("Verify the state of phone number field under Essential Claims")
	public void verifyPhoneNumberFieldNonEditable() {
		Assert.assertTrue(loginOptionsPage.isPhoneNumberFieldNonEditable(),
				"Phone Number field is editable, but should be non-editable (label expected).");
	}

	@When("user click on cancel button in consent screen")
	public void userClickOnCancelBtn() {
		loginOptionsPage.clickOnCancelBtnInConsentScreen();
	}

	@Then("verify the header Please Confirm is displayed")
	public void verifyWarningMessage() {
		assertTrue(loginOptionsPage.isCancelWarningHeaderDisplayed());
	}

	@Then("verify the message Are you sure you want to discontinue the login process? is displayed")
	public void verifyWarningHeader() {
		assertTrue(loginOptionsPage.isCancelWarningMessageDisplayed());
	}

	@Then("verify discontinue button is displayed")
	public void verifyDiscontinueButton() {
		assertTrue(loginOptionsPage.isDiscontinueButtonDisplayed());
	}

	@Then("verify stay button is displayed")
	public void verifWarningHeader() {
		assertTrue(loginOptionsPage.isStayButtonDisplayed());
	}

	@When("user click on Stay button")
	public void userClickOnStayBtn() {
		loginOptionsPage.clickOnStayBtnInConsentScreen();
	}

	@Then("verify user is retained on same consent screen")
	public void verifyOnSameConsentScreen() {
		assertTrue(loginOptionsPage.isRetainsOnConsentSceenDisplayed());
	}

	@Then("user click on Discontinue button")
	public void userClickOnDiscontinueBtn() {
		loginOptionsPage.clickOnDiscontinueButton();
	}

	@And("verify user is redirected to relying party portal page")
	public void verifyRedirectedToMainScreen() {
		assertTrue(loginOptionsPage.isHealthPortalPageDisplayed());
	}

	@When("user enables the master toggle of voluntary claims")
	public void enableMasterToggleOfVoluntaryClaims() {
		loginOptionsPage.enableMasterToggleVoluntaryClaims();
	}

	@And("click on allow button in consent page")
	public void userClickOnAllowButton() {
		loginOptionsPage.clickOnAllowBtnInConsentScreen();
	}

	@Then("verify user is navigated to landing page of relying party")
	public void verifyRedirectedToRelyingPartyPage() {
		assertTrue(loginOptionsPage.isWelcomePageDisplayed());
	}

	@And("verify welcome message is displayed with the registered name")
	public void verifyWelcomeWithNameIsDisplayed() {
		assertTrue(loginOptionsPage.isWelcomePageDisplayed());
	}

}
