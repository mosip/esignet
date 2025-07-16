package stepdefinitions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.mosip.testrig.apirig.testrunner.OTPListener;
import pages.LoginOptionsPage;
import pages.RegistrationPage;
import pages.SignUpPage;

public class SignUpStepDef {

	public WebDriver driver;
	BaseTest baseTest;
	SignUpPage signUpPage;
	LoginOptionsPage loginOptionsPage;
	RegistrationPage registrationPage;

	public SignUpStepDef(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		signUpPage = new SignUpPage(driver);
		loginOptionsPage = new LoginOptionsPage(driver);
		registrationPage = new RegistrationPage(driver);
	}

	@Then("verify Sign-Up with Unified Login option should be displayed")
	public void signUpWithUnifiedLoginOptionDisplayed() {
		assertTrue(loginOptionsPage.isSignUpWithUnifiedLoginOptionDisplayed());
	}

	@Given("user clicks on the Sign-Up with Unified Login hyperlink")
	public void userClicksOnSignUpWithUnifiedLoginHyperlink() {
		loginOptionsPage.clickOnSignUpWithUnifiedLogin();
	}

	@Then("verify user is navigated to the Mobile Number Registration screen")
	public void userShouldBeNavigatedToRegistrationScreen() {
		assertTrue(registrationPage.isRegistrationScreenDisplayed());
	}

	@Then("user verify header text")
	public void headerInRegistrationPageDisplayed() {
		assertTrue(registrationPage.isHeaderInRegistrationPageDisplayed());
	}

	@Then("user verify a text box for entering an 8–9 digit mobile number")
	public void validateMobileTextBox() {
		assertTrue(registrationPage.isEnterMobileNumberTextBoxDisplayed());
	}

	@Then("user verify a Continue button")
	public void validateContinueButton() {
		assertTrue(registrationPage.isContinueButtonVisible());
	}

	@Then("user verify an option to navigate back to the previous screen")
	public void validateBackNavigationOption() {
		assertTrue(registrationPage.isBackOptionAvailable());
	}

	@Then("user verify an option to select preferred language")
	public void validateLanguageSelectionOption() {
		assertTrue(registrationPage.isLanguageSelectionVisible());
	}

	@Then("user verify footer text and logo")
	public void validateFooterText() {
		assertTrue(registrationPage.isFooterTextDisplayed());
		assertTrue(registrationPage.isFooterLogoDisplayed());
	}

	@Then("user verify the mobile number text field should be pre-filled with country code")
	public void verifyPrefilledCountryCodeInMobileField() {
		assertTrue(registrationPage.isTextBoxPrefilledWithCountryCode());
	}

	@Then("user verify the help text in mobile number text field is displayed")
	public void verifyHelpTextInMobileNumberTextBox() {
		assertTrue(registrationPage.isHelpTextInMobileNumberTextBoxDisplayed("Enter 8-9 digit mobile number"));
	}

	String mobileNumber;

	@When("user enters {string} in the mobile number text box")
	public void userEntersMobileNumber(String number) {
		this.mobileNumber = number;
		registrationPage.enterMobileNumber(number);
	}

	@Then("user tabs out")
	public void userTabsOutOfMobileField() {
		registrationPage.clickOnOutsideMobileField();
	}

	@Then("verify the error message Enter valid username is displayed")
	public void errorMessageShouldBeDisplayed() {
		assertTrue(registrationPage.isErrorMessageDisplayed());
	}

	@Then("validate that the Continue button remain disabled")
	public void continueButtonShouldBeDisabled() {
		assertFalse(registrationPage.isContinueButtonEnabled());
	}

	@Then("the placeholder will be replaced with the entered mobile number")
	public void placeholderShouldDisappear() {
		assertTrue(registrationPage.isPlaceholderGone());
	}

	@Then("validate that the Continue button enabled")
	public void continueButtonShouldBeEnabled() {
		assertTrue(registrationPage.isContinueButtonEnabled());
	}

	@Then("verify no error message is displayed")
	public void noErrorMessageShouldBeDisplayed() {
		assertFalse(registrationPage.isErrorMessageDisplayed());
	}

	@Then("verify the error Number cannot start with zero.Enter valid username is shown")
	public void numberCannotStartWithZeroErrorShouldBeDisplayed() {
		assertTrue(registrationPage.isZeroErrorMessageDisplayed());
	}

	@Then("verify the mobile number field should contain only 9 digits")
	public void verifyMaxDigitsAllowed() {
		assertTrue(registrationPage.isNumberRestrictedToNineDigits());
	}

	@Then("verify the mobile number field should remain empty or accept only number")
	public void fieldShouldNotAcceptSpecialCharacters() {
		assertTrue(registrationPage.isMobileFieldEmptyOrUnchanged());
	}

	@Then("verify the mobile number field should contain only numeric characters")
	public void fieldShouldContainOnlyDigits() {
		assertTrue(registrationPage.isMobileFieldContainingOnlyDigits());
	}

	@When("user clicks on the navigate back button")
	public void userClicksNavigateBackButton() {
		registrationPage.clickOnNavigateBackButton();
	}

	@Then("verify user is redirected to the previous screen")
	public void verifyUserIsRedirectedToPreviousScreen() {
		assertTrue(registrationPage.isPreviousScreenVisible());
	}

	@When("user clicks the browser back button")
	public void userClicksTheBrowserBackButton() {
		registrationPage.browserBackButton();
	}

	@Then("user clicks on the Continue button")
	public void userClickOnContinueButton() {
		registrationPage.clickOnContinueButton();
	}

	@Then("verify user is navigated to the OTP screen")
	public void userIsNavigatedToOtpPage() {
		assertTrue(registrationPage.isEnterOtpPageDisplayed());
	}

	@Given("user is on the OTP screen")
	public void userIsOnOtpScreen() {
		assertTrue(registrationPage.isEnterOtpPageDisplayed());
	}

	@Then("user verifies the OTP screen header is displayed as Enter OTP")
	public void verifyOtpHeader() {
		assertTrue(registrationPage.isOtpPageHeaderDisplayed());
	}

	@Then("user verifies the OTP screen description should contain a masked mobile number")
	public void verifyOtpDescriptionMasked() {
		assertTrue(registrationPage.isOtpPageDescriptionDisplayed());
	}

	@Then("user verifies the OTP input field is visible")
	public void verifyOtpInputVisible() {
		assertTrue(registrationPage.isOtpInputFieldVisible());
	}

	@Then("user verifies the Verify OTP button is visible")
	public void verifyButtonVisible() {
		assertTrue(registrationPage.isVerifyOtpButtonVisible());
	}

	@Then("user verifies a 3-minute countdown timer is displayed")
	public void verifyTimerDisplayed() {
		assertTrue(registrationPage.isCountdownTimerDisplayed());
	}

	@Then("user verifies the Resend OTP option is visible")
	public void verifyResendOptionVisible() {
		assertTrue(registrationPage.isResendOtpOptionVisible());
	}

	@Then("user verifies an option to go back and update the mobile number is be present")
	public void verifyBackOptionVisible() {
		assertTrue(registrationPage.isBackToEditMobileNumberOptionVisible());
	}

	@When("user clicks the back button on the OTP screen")
	public void userClicksBackButtonOnOtpScreen() {
		registrationPage.clickOnNavigateBackButton();
	}

	@Then("verify user is redirected back to the Registration screen")
	public void userIsRedirectedToRegistrationScreen() {
		assertTrue(registrationPage.isRegistrationScreenDisplayed());
	}

	@Then("user waits for OTP timer to expire")
	public void userWaitsForOtpToExpire() {
		registrationPage.waitUntilOtpExpires();
	}

	@When("user enters {string} as a Otp")
	public void userEntersOtp(String otp) {
		registrationPage.enterOtp(otp);
	}

	@Then("user clicks on the Verify OTP button")
	public void userClicksOnVerifyOtpButton() {
		registrationPage.clickOnVerifyOtpButton();
	}

	@Then("verify an error message OTP expired. Please request a new one and try again. is displayed at the top")
	public void verifyOtpExpiredErrorMessage() {
		assertTrue(registrationPage.isOtpExpiredMessageDisplayed());
	}

	@Then("user clicks on the Resend OTP button")
	public void userClicksOnResendButton() {
		registrationPage.clickOnResendOtpButton();
	}

	@Then("verify an error message OTP authentication failed. Please try again. is displayed at the top")
	public void verifyIncorrectOtpMessage() {
		assertTrue(registrationPage.isIncorrectOtpErrorDisplayed());
	}

	@Then("verify OTP field is rejecting special characters")
	public void otpFieldRemainsEmptyAfterEntreingSpecialCharacters() {
		assertTrue(registrationPage.isOtpFieldEmptyOrUnchanged());
	}

	@Then("verify OTP field is rejecting alphabets")
	public void otpFieldRemainsEmptyAfterEntreingAlphabets() {
		assertTrue(registrationPage.isOtpFieldEmptyfterAlphabetEntry());
	}

	@Then("verify OTP field is rejecting alphanumeric characters")
	public void otpFieldRemainsEmptyAfterEntreingAlphaNumeric() {
		assertTrue(registrationPage.isOtpFieldsNumericOnly());
	}

	@Then("validate the {string} button is disabled")
	public void verifyButtonShouldBeDisabled(String buttonText) {
		registrationPage.waitForButtonToBecomeDisabled(registrationPage.getVerifyOtpButton(), 3);
		assertFalse(registrationPage.isVerifyOtpButtonEnabled());
	}

	@When("user enters the complete 6-digit OTP")
	public void userEntersOtp() {
		registrationPage.enterOtp(OTPListener.getOtp(mobileNumber));
	}

	@Then("verify OTP is masked as soon as it is entered")
	public void verifyOtpMaskedOnEntry() {
		assertTrue(registrationPage.isOtpMasked());
	}

	@Then("validate the {string} button is enabled")
	public void verifyButtonShouldBeEnabled(String buttonText) {
		assertTrue(registrationPage.isVerifyOtpButtonEnabled());
	}

	@Then("verify Sign-Up Failed! is displayed as a heading")
	public void verifyHeaderText() {
		assertTrue(registrationPage.isFailureHeaderDisplayed());
	}

	@Then("verify the failure message The provided mobile number is already registered. Please use the Login option to proceed. shown")
	public void verifyFailureMessage() {
		assertTrue(registrationPage.isFailureMessageDisplayed());
	}

	@Then("verify a Login button is visible")
	public void verifyLoginButtonDisplayed() {
		assertTrue(registrationPage.isLoginButtonVisible());
	}

	@Then("user clicks on the Login button")
	public void userClicksOnLoginButton() {
		registrationPage.clickOnLoginButtonInSignUpFailedScreen();
	}

	@Then("verify user is redirected to the success screen")
	public void userShouldBeRedirectedToSuccessScreen() {
		assertTrue(registrationPage.isSuccessScreenDisplayed());
	}

	@Then("verify the header Successful! is displayed")
	public void theHeaderShouldBeDisplayed() {
		assertTrue(registrationPage.isSuccessHeaderDisplayed());
	}

	@Then("verify the message Your mobile number has been verified successfully. Please continue to setup your account and complete the registration process. is displayed")
	public void theMessageShouldBeDisplayed() {
		assertTrue(registrationPage.iSuccessMessageDisplayed());
	}

	@Then("verify a Continue button is displayed")
	public void continueButtonShouldBeDisplayed() {
		assertTrue(registrationPage.isContinueButtonDisplayed());
	}

}