package stepdefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.testng.Assert;
import org.openqa.selenium.WebDriver;

import base.BasePage;
import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.java.en.And;
import io.mosip.testrig.apirig.testrunner.OTPListener;
import pages.s1pages;
import pages.r1pages;
import pages.SignUpPage;

public class s1stepdefinition {

	public WebDriver driver;
	BaseTest baseTest;
	SignUpPage signUpPage;
	BasePage basePage;
	s1pages s1pages;
	r1pages r1pages;

	public s1stepdefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		signUpPage = new SignUpPage(driver);
		basePage = new BasePage(driver);
		s1pages = new s1pages(driver);
		r1pages = new r1pages(driver);
	}

	@Given("user is on relying party portal")
	public void userIsOnRelyingPartyPortal() {
		Assert.assertTrue(s1pages.isUserOnRelyingPortal(), "User is not on relying party portal");
	}

	@Then("user clicks on login button in relying portal page")
	public void userClicksOnLoginButtonInRelyingPortalPage() {
		s1pages.clickOnSignInEsignetButton();
	}

	@Then("user redirect and verify that Sign-Up with unified login button should display")
	public void userVerifyThatSignUpWithUnifiedLoginButtonShouldDisplay() {
		Assert.assertTrue(s1pages.isSignUpWithUnifiedLoginButtonDisplayed());
	}

	@Then("user clicks on Sign-Up with unified login button hyperlink")
	public void userClicksOnSignUpWithUnifiedLoginButtonHyperlink() {
		s1pages.clickOnSignUpWithUnifiedLoginButton();
	}

	@Then("user will redirected to mobile number registration page")
	public void userWillBeRedirectedToMobileNumberRegistrationPage() {
		assertTrue("User is not redirected to Mobile Number Registration Page",
				r1pages.isRedirectedToMobileNumberRegistrationPage());
	}

	@Then("user verify Header text in same page")
	public void userVerifyHeaderTextInSamePage() {
		Assert.assertTrue(r1pages.isHeaderInRegistrationScreenDisplayed());
	}

	@Then("user verify Enter 8-9 digit number is visible")
	public void userVerifyEnterEightNineDigitNumberIsVisible() {
		Assert.assertTrue(r1pages.isEnterMobileNumberTextFieldDisplayed());
	}

	@Then("user verify continue button is visible")
	public void userVerifyContinueButtonIsVisible() {
		Assert.assertTrue(r1pages.isContinueButtonDisplayedInRegistationPage());
	}

	@Then("user will check previous navigate arrow is visible")
	public void userWillCheckPreviousNavigateArrowIsVisible() {
		Assert.assertTrue(r1pages.isBackButtonDisplayed());
	}

	@Then("user verify for preferred language option is visible")
	public void userVerifyForPreferredLanguageOptionIsVisible() {
		Assert.assertTrue(r1pages.isLanguageSelectionOptionDisplayed());
	}

	@Then("user verify footer text powered by esignet logo display")
	public void userVerifyFooterTextPoweredByEsignetLogoDisplay() {
		Assert.assertTrue(r1pages.isFooterTextDisplayed());
		Assert.assertTrue(r1pages.isFooterLogoDisplayed());
	}

	@Then("user verify mobile number text should prefilled with country code")
	public void userVerifyMobileNumberTextShouldPrefilledWithCountryCode() {
		Assert.assertTrue(r1pages.isPrefilledCountryCodeDisplayed());
	}

	String mobileNumber;

	@When("user enters {string} in the mobile text field")
	public void userEntersInMobileTextField(String number) {
		this.mobileNumber = number;
		r1pages.enterMobileNumber(number);
	}

	@Then("validate that Enter a valid username error massage should display")
	public void validateEnterValidUsernameErrorIsDisplayed() {
		Assert.assertTrue(r1pages.isEnterValidUsernameErrorDisplayed(),
				"Enter a valid username error message is not displayed!");
	}

	@Then("user tabs out on mobile field")
	public void userTabsOut() {
		r1pages.userTabsOut();
	}

	@Then("continue button remains disabled")
	public void continueButtonShouldBeDisabled() {
		assertFalse(r1pages.isContinueButtonEnabled());
	}

	@Then("validate that Number cannot start with zero. Enter a valid mobile number. massage will display")
	public void validateThatNumberCannotStartWithZeroErrorMessageShouldDisplay() {
		Assert.assertTrue(r1pages.isNumberCannotStartWithZeroErrorDisplayed(),
				"Number cannot start with zero error message was not displayed!");
	}

	@When("user clicks on navigate back button")
	public void userClicksOnNavigateBackButton() {
		r1pages.clickOnBackButton();
	}

	@Then("user will redirected to previous screen")
	public void userWillRedirectedToPreviousScreen() {
		Assert.assertTrue(r1pages.isPreviousScreenVisible());
	}

	@When("user clicks on browser back button")
	public void userClicksTheBrowserBackButton() {
		basePage.browserBackButton();
	}

	@Then("user clicks on continue button")
	public void userClicksOnContinueButton() {
		r1pages.clickContinueButton();
	}

	@Given("user is on OTP screen")
	public void userIsOnOtpScreen() {
		assertTrue(r1pages.isEnterOtpPageDisplayed());
	}

	@Then("user navigated to enter OTP header screen")
	public void userNavigatedToEnterOtpScreen() {
		Assert.assertTrue(r1pages.isOtpPageVisible());
	}

	@Then("user enters {string} in the OTP field")
	public void userEntersInTheOtpFieldScreen(String otp) {
		r1pages.enterOtp(otp);
	}

	@Then("user verifies the OTP screen description should contain a masked mobile number verify it")
	public void verifyOtpDescriptionMasked() {
		assertTrue(r1pages.isEnterOtpPageDisplayed());
	}

	@Then("user verify OTP input field is visible")
	public void userVerifyOtpInputFieldIsVisible() {
		Assert.assertTrue(r1pages.isOtpInputFieldVisible());
	}

	@Then("user verifies the verify OTP field is visible")
	public void userVerifiesTheVerifyOtpFieldIsVisible() {
		Assert.assertTrue(r1pages.isVerifyOtpButtonVisible());
	}

	@Then("user verify the time count down for 3 minutes")
	public void userVerifyTheTimeCountDownForThreeMinutes() {
		Assert.assertTrue(r1pages.isOtpCountDownTimerVisible());
	}

	@Then("user verifies for Resend OTP is visible")
	public void userVerifiesForResendOtpIsVisible() {
		Assert.assertTrue(r1pages.isResendOtpButtonVisible());
	}

	@Then("user go back and update the mobile number")
	public void verifyBackOptionVisible() {
		Assert.assertTrue(r1pages.isBackToEditMobileNumberOptionVisible());

	}

	@When("user clicks on back button on OTP screen")
	public void userClicksOnBackButtonOnOtpScreen() {
		r1pages.clickBackButtonOnOtpScreen();
	}

	@Then("user waits for OTP time expire")
	public void userWaitsForOtpTimeExpire() {
		r1pages.waitUntilOtpExpires();
	}

	@Then("user enters {string} in the OTP field screen")
	public void userEntersOtp(String otp) {
		r1pages.enterOtp(otp);
	}

	@Then("user check that verify button comes to enable state")
	public void userEnableVerifyOtp() {
		r1pages.isVerifyOtpButtonEnabled();
	}

	@Then("user clicks on verify OTP button quickly")
	public void userClicksOnVerifyOtpButton() {
		r1pages.clickOnVerifyOtpButton();
	}

	@Then("verify an error massage OTP expired. Please request a new one and try again")
	public void verifyOtpExpiredErrorMessages() {
		assertTrue(r1pages.isOtpExpiredMessageDisplayed());
	}

	@When("user click on the close icon of the error message")
	public void userClicksOnErrorCloseIcon() {
		r1pages.clickOnErrorCloseIcon();
	}

	@Then("verify the error message is not display")
	public void verifyTheErrorMessageIsNotDisplay() {
		r1pages.verifyErrorMessageIsNotDisplayed();
	}

	@Then("user verify that OTP field rejecting specialCharacter")
	public void otpFieldRemainsEmptyAfterEntreingSpecialCharacters() {
		assertTrue(r1pages.isOtpFieldEmptyOrUnchanged());
	}

	@Then("user verify that OTP field rejecting alphabet")
	public void otpFieldRemainsEmptyAfterEntreingAlphabets() {
		assertTrue(r1pages.isOtpFieldEmptyfterAlphabetEntry());
	}

	@Then("user verify that OTP field rejecting alphaNumericCharacter")
	public void otpFieldRemainsEmptyAfterEntreingAlphaNumeric() {
		assertTrue(r1pages.isOtpFieldsNumericOnly());
	}

	@And("user verify that Successful! is display")
	public void userVerifyThatSuccessfulMessageVisible() {
		Assert.assertTrue(r1pages.checkSuccessfulMessageVisible());

	}

	@Then("user verify that Your mobile number has been verified successfully massage will showing")
	public void userVerifyThatYourMobileNumberHasBeenVerifiedSuccessfullyMassageWillShowing() {
		Assert.assertTrue(r1pages.isMobileNumberVerifiedMessageDisplayed());
	}

	@Then("user verify that continue button display in success screen")
	public void userVerifyThatContinueButtonDisplayInSuccessScreen() {
		Assert.assertTrue(r1pages.isContinueButtonOnSuccessScreenDisplayed());

	}

	@And("user will redirected to successful screen")
	public void userWillRedirectedToSuccessfulScreen() {
		Assert.assertTrue(r1pages.checkSuccessfulMessageVisible());

	}

	@And("user clicks on continue button in successful screen")
	public void userClicksOnContinueButtonInSuccessfulScreen() {
		r1pages.clickContinueButtonOnSuccessScreen();
	}

	@Then("user verify in header Setup Account display")
	public void userVerifyInHeaderSetupAccountDisplay() {
		Assert.assertTrue(r1pages.isSetUpAccountHeaderDisplayed());
	}

	@Then("user verify Please enter the requested details to complete your registration display")
	public void userVerifyPleaseEnterTheRequestedDetailsToCompleteYourRegistrationDisplay() {
		Assert.assertTrue(r1pages.isAccountHeaderMassageDisplay());
	}

	@Then("user verify that Username field will display")
	public void userVerifyThatUsernameFieldWillDisplay() {
		Assert.assertTrue(r1pages.isUserNameVisible());
	}

	@And("user verify that enter fullname in Khmer display")
	public void userVerifyThatEnterFullnameInKhmerDisplay() {
		Assert.assertTrue(r1pages.isFullNameInKhmerVisible());
	}

	@And("user verify setup Password will display")
	public void userVerifySetupPasswordWillDisplay() {
		Assert.assertTrue(r1pages.isPasswordFieldVisible());
	}

	@Then("user verify option to confirm password display")
	public void userVerifyOptionToConfirmPasswordDisplay() {
		Assert.assertTrue(r1pages.isConfirmPasswordVisible());
	}

	@Then("user verify checks to terms and condition privacy policy")
	public void userVerifyChecksToTermsAndConditionPrivacyPolicy() {
		Assert.assertTrue(r1pages.isTermsAndConditionCheckBoxVisible());
	}

	@Then("user verify continue button in setupAccount page")
	public void userVerifyContinueButtonInSetupAccountPage() {
		Assert.assertTrue(r1pages.isContinueButtonInSetUpAccountPageVisible());
	}

	@Then("user enters {string} in the setupAccount page")
	public void userEntersValidFullNameInKhmerInSetupAccountPage(String fullNameInKhmer) {
		r1pages.enterFullNameInKhmer(fullNameInKhmer);
	}

	@Then("user enters {string} in the same page")
	public void userEntersValidPasswordInSetupAccountPage(String validPassword) {
		r1pages.enterPassword(validPassword);
	}

	@Then("user enters {string} in account screen")
	public void userEntersConfirmPasswordInSetupAccountPage(String confirmPassword) {
		r1pages.enterPasswordDisplay(confirmPassword);
	}

	@Then("user clicks on CheckBox of terms and condition and privacy policy in setupAccount")
	public void userClicksOnCheckBoxOfTermsAndConditionAndPrivacyPolicyInSetupAccountPage() {
		r1pages.clickOnTermsAndConditionCheckBox();
	}

	@Then("continue button remains enabled in setup page")
	public void continueButtonRemainsEnabledInSetupAccountPage() {
		r1pages.continueButtonRemainsEnabled();
	}

	@Then("user clicks on continue button in same screen")
	public void userClicksOnContinueButtonInSetupAccountPage() {
		r1pages.clickOnContinueButtonInSetupAccountPage();
	}

	@Then("it will redirected to congratulations page")
	public void itWillRedirectedToCongratulationsPage() {
		Assert.assertTrue(r1pages.isCongratulationScreenDisplayed());
	}

	@Then("user clicks on login button in congratulations page")
	public void userClicksOnLoginButtonInCongratulationsPage() {
		r1pages.clickOnLoginButtonInSuccessPage();
	}

	@Then("it will display in header Sign-Up Failed!")
	public void verifySignUpFailureHeader() {
		Assert.assertTrue(r1pages.isSignUpFailedHeaderDisplayed());
	}

	@Then("provided mobile number is already registered Please use the Login option to proceed will show")
	public void providedMobileNumberAlreadyRegisteredMessageShouldBeDisplayed() {
		Assert.assertTrue(r1pages.isSignUpFailedMessageDisplayed());
	}

	@Then("ok button will display on same page")
	public void loginButtonWillDisplayOnSamePage() {
		Assert.assertTrue(r1pages.isOkButtonDisplayed());
	}

}