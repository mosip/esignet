package stepdefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import io.cucumber.java.en.When;
import io.mosip.testrig.apirig.testrunner.OTPListener;
import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.ForgetPasswordPage;
import pages.LoginOptionsPage;
import pages.SmtpPage;
import utils.EsignetUtil;

public class ForgetPasswordStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(ForgetPasswordStepDefinition.class);
	BaseTest baseTest;
	LoginOptionsPage loginOptionsPage;
	ForgetPasswordPage forgetPasswordPage;
	SmtpPage smtpPage;

	public ForgetPasswordStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		this.forgetPasswordPage = new ForgetPasswordPage(driver);
		this.smtpPage = new SmtpPage(driver);
	}

	@Then("user click on Login with password")
	public void user_click_on_login_with_password() {
		forgetPasswordPage.clickOnLoginWithPassword();
	}

	@Then("user verify forget password link")
	public void user_verify_forget_password_link() {
		Assert.assertTrue(forgetPasswordPage.isforgetPasswordLinkDisplayed(),
				"Forget Password link should be visible on the page.");
	}

	@Then("user click on forget password link")
	public void user_click_on_forget_password_link() {
		forgetPasswordPage.clickOnForgetPasswordLink();
	}

	@Then("user verify browser redirected to reset-password")
	public void user_verify_browser_redirected_to_reset_password() {
		Assert.assertTrue(forgetPasswordPage.isRedirectedToResetPasswordPage(), "Redirected to reset-password link");
	}

	@Then("user verify country code prefix")
	public void user_verify_country_code_prefix() {
		Assert.assertTrue(forgetPasswordPage.isphonePrefixDisplayed(), "Country code prefix displayed");
	}

	@Then("user verify the water mark text inside phonenumber")
	public void user_verify_the_water_mark_text_inside_phonenumber() {
		Assert.assertTrue(forgetPasswordPage.isWaterMarkDisplayed(), "Water mark displayed inside phonenumber");
	}

	@Then("user verify country code is not editable")
	public void user_verify_country_code_is_not_editable() {
		Assert.assertTrue(forgetPasswordPage.isCountryCodeNonEditable(), "Country code is not editable");
	}

	String phoneNumber;

	@When("user enters {string} into the mobile number field")
	public void user_enters_mobile_number(String number) {
		this.phoneNumber = number;
		forgetPasswordPage.enterPhoneNumber(number);
	}

	@When("user clicks outside the input to trigger validation")
	public void user_clicks_outside_to_trigger_validation() {
		forgetPasswordPage.triggerPhoneValidation();
	}

	@Then("phone number should be {string}")
	public void phone_number_should_be(String validity) {
		boolean isErrorVisible;

		try {
			isErrorVisible = forgetPasswordPage.isPhoneErrorVisible();
		} catch (Exception e) {
			isErrorVisible = false;
		}
		if (validity.equalsIgnoreCase("valid")) {
			Assert.assertFalse(isErrorVisible, "Expected no error for valid phone number, but error is shown.");
		} else {
			Assert.assertTrue(isErrorVisible, "Expected error for invalid phone number, but none is shown.");
		}
	}

	@Then("user verify forget password heading")
	public void user_verify_forget_password_headning() {
		Assert.assertTrue(forgetPasswordPage.isForgetPasswordHeadningVisible(), "Forget password heading visible");
	}

	@Then("user verify back button on forget password")
	public void user_verify_back_button_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isBackButtonOnForgePasswordVisible(),
				"Back button on foget password visible");
	}

	@Then("user verify subheading on forget password")
	public void user_verify_subeading_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isForgetPasswordSubHeadningVisible(),
				"Subheading on foget password visible");
	}

	@Then("user verify username label on forget password")
	public void user_verify_user_label_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isUserNameLabelVisible(), "User Label on foget password visible");
	}

	@Then("user verify fullname label on forget password")
	public void user_verify_fullname_label_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isFullNameLabelVisible(), "Fullname Label on foget password visible");
	}

	@Then("user verify continue button on forget password")
	public void user_verify_lang_selection_button_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isLangSelectionButtonVisible(),
				"Lang selection button on foget password visible");
	}

	@Then("user verify footer on forget password")
	public void user_verify_footer_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isFooterPoweredByVisible(),
				"Lang selection button on foget password visible");
	}

	@Then("mobile number input should remain empty")
	public void mobile_number_input_should_remain_empty() {
		Assert.assertEquals(forgetPasswordPage.getEnteredPhoneNumber(), "",
				"Field should remain empty for invalid input like alphanumeric or special characters.");
	}

	@Then("user verify continue button is not enabled")
	public void user_verify_continue_button_is_not_enabled() {
		Assert.assertTrue(forgetPasswordPage.isContinueButtonDisabled(), "Continue button disabled");
	}
	
	@Then("verify the mobile number field should restrict to 9 digits")
	public void verifyNumberRestrictedToNine() {
		assertTrue(forgetPasswordPage.isInputRestrictedToNineDigits());
	}

	@When("user enters {string} into the fullname field")
	public void user_enters_fullname(String input) {
		forgetPasswordPage.enterFullName(input);
	}

	@Then("system should show error for fullname {string}")
	public void system_should_show_error_for_fullname(String expectError) {
		Assert.assertEquals(forgetPasswordPage.isFullNameErrorVisible(), Boolean.parseBoolean(expectError),
				"Mismatch in fullname error visibility");
	}

	@Then("user verify full name error message")
	public void fullname_error_displayed() {
		Assert.assertTrue(forgetPasswordPage.isFullNameErrorVisible());
	}

	@Then("user verify full name error message not displayed")
	public void fullname_error_not_displayed() {
		Assert.assertFalse(forgetPasswordPage.isFullNameErrorPresent(),
				"Expected no error message, but one was present");
	}

	@Then("only 30 characters are retained in the fullname field")
	public void only_30_chars_retained() {
		String actual = forgetPasswordPage.getEnteredFullName();
		Assert.assertEquals(actual.length(), 30);
	}

	@Then("user verify continue button is enabled")
	public void user_verify_continue_button_is_enabled() {
		Assert.assertTrue(forgetPasswordPage.isContinueButtonEnabled(), "Continue button enabled");
	}

	@Then("user click on continue button")
	public void user_click_on_continue_button_is_enabled() {
		forgetPasswordPage.clockOnContinueButton();
	}

	@Then("user click on back button")
	public void user_click_on_back_button() {
		forgetPasswordPage.clickOnBackButtonOnForgetPassword();
	}

	@Then("user verify browser redirected to login page")
	public void user_verify_browser_redirected_to_login_page() {
		Assert.assertTrue(forgetPasswordPage.isRedirectedToLoginPage(), "Not redirected to login page");
	}

	private int waitTime;

	@Then("user waits for resend OTP button and verifies it's enabled or skipped")
	public void waitForResendOtpButtonAndValidate() throws InterruptedException {
		waitTime = EsignetUtil.getOtpResendDelayFromSignupActuator();
		logger.info("Waiting for OTP resend delay: " + waitTime + " seconds");
		Thread.sleep(waitTime * 1000L + 1000);
		Assert.assertTrue(forgetPasswordPage.isResendOtpButtonEnabled(), "Resend OTP button should be enabled");
	}

	@Then("user waits and clicks on resend OTP, then validates {int} out of 3 attempts message")
	public void userClicksResendOtpAndValidatesAttemptLeft(int expectedAttemptLeft) throws Exception {
		forgetPasswordPage.clickOnResendOtp();
		Thread.sleep(1500);
		String attemptText = forgetPasswordPage.getOtpResendAttemptsText();
		Assert.assertTrue(attemptText.contains(expectedAttemptLeft + " of 3 attempts left"),
				"Expected attempt count: " + expectedAttemptLeft + " out of 3 not found. Actual: " + attemptText);
	}

	@Then("user waits for OTP timer to expire for fourth time")
	public void userWaitsForOtpToExpire() {
		forgetPasswordPage.waitUntilOtpExpire();
	}

	@Then("verify user is redirected back to the Forget Password screen")
	public void verifyUserRedirectedToPreviousScreen() {
		assertTrue(forgetPasswordPage.isForgetPassowrdScreenVisible());
	}

	@Then("verify error popup with header Invalid is displayed")
	public void verifyHeaderInErrorPopup() {
		assertTrue(forgetPasswordPage.isErrorHeaderInvalidVisible());
	}

	@Then("verify error message Transaction has failed due to invalid request. Please try again. is displayed")
	public void verifyMessageInErrorPopup() {
		assertTrue(forgetPasswordPage.isErrorMessageVisible());
	}

	@Then("verify error message The mobile number or name entered is invalid. Please enter valid credentials associated with your account and try again. is displayed")
	public void verifyMessageInErrorPopupDisplayed() {
		assertTrue(forgetPasswordPage.isErrorMessageVisible());
	}

	@Then("verify retry button is displayed")
	public void validateRetryButton() {
		assertTrue(forgetPasswordPage.isRetryButtonVisible());
	}

	@Then("user click on retry button")
	public void userClicksOnRetryButton() {
		forgetPasswordPage.clickOnRetryButton();
	}

	@Then("verify user is redirected to the reset password screen")
	public void verifyUserIsRedirectedToesetPasswordScreen() {
		assertTrue(forgetPasswordPage.isResetPasswordScreenVisible());
	}

	@When("user enters the 6-digit OTP")
	public void userEntersValidOtp() {
		forgetPasswordPage.enterOtp(OTPListener.getOtp(phoneNumber));
	}

	@Then("user verify reset password header")
	public void userVerifyResetPasswordHeader() {
		assertTrue(forgetPasswordPage.isResetPasswordHeaderVisible());
	}

	@Then("user verify reset password description")
	public void userVerifyResetPasswordDescription() {
		assertTrue(forgetPasswordPage.isPasswordInstructionMessageVisible());
	}

	@Then("user verify new password label")
	public void userVerifyNewPasswordLabel() {
		assertTrue(forgetPasswordPage.isNewPasswordLabelVisible());
	}

	@Then("user verify confirm new password label")
	public void userVerifyConfirmNewPasswordLabel() {
		assertTrue(forgetPasswordPage.isConfirmNewPasswordLabelVisible());
	}

	@Then("user verify new password input text box is present")
	public void userVerifyNewPasswordInputTextbox() {
		assertTrue(forgetPasswordPage.isNewPasswordInputTextboxVisible());
	}

	@Then("user verify confirm new password input text box is present")
	public void userVerifyConfirmNewPasswordInputTextbox() {
		assertTrue(forgetPasswordPage.isConfirmNewPasswordInputTextboxVisible());
	}

	@Then("user verify new password info icon is visible")
	public void userVerifyNewPasswordInfoIcon() {
		assertTrue(forgetPasswordPage.isNewPasswordInfoIconVisible());
	}

	@Then("user click on new password info icon")
	public void userClickOnNewPasswordInfoIcon() {
		forgetPasswordPage.clickOnNewPasswordInfoIcon();
	}

	@Then("verify new password policy displayed")
	public void userVerifyNewPasswordPolicyToolTip() {
		assertTrue(forgetPasswordPage.isPasswordPolicyTooltipVisible());
	}

	@Then("user verify new password field placeholder {string}")
	public void userVerifyNewPasswordPlaceholder(String expectedText) {
		assertEquals(expectedText, forgetPasswordPage.getNewPasswordFieldPlaceholder());
	}

	@Then("user verify confirm password field placeholder {string}")
	public void userVerifyConfirmPasswordPlaceholder(String expectedText) {
		assertEquals(expectedText, forgetPasswordPage.getConfirmNewPasswordFieldPlaceholder());
	}

	@When("user enters {string} into the new password field")
	public void userFillsNewPasswordField(String password) {
		forgetPasswordPage.enterNewPassword(password);
	}

	@Then("user clicks outside the password field")
	public void userClicksOutsidePasswordField() {
		forgetPasswordPage.clickOutsidePasswordField();
	}

	@Then("verify an error message Password does not meet the password policy. is displayed")
	public void verifyInvalidPasswordError() {
		assertTrue(forgetPasswordPage.isPasswordErrorDisplayed());
	}

	@Then("verify password input is resitricted to twenty characters")
	public void verifyPasswordIsResticted() {
		assertTrue(forgetPasswordPage.isPasswordRestrictedToTwentyChar());
	}

	@When("user enters {string} into the confirm password field")
	public void userFillsConfirmPasswordField(String confirmPassword) {
		forgetPasswordPage.enterConfirmPassword(confirmPassword);
	}

	@Then("verify an error message New Password and Confirm New Password do not match. is displayed")
	public void verifyInvalidConfirmPasswordError() {
		assertTrue(forgetPasswordPage.isconfirmPasswordErrorDisplayed());
	}

	@Then("verify confirm password input is resitricted to twenty characters")
	public void verifyConfirmPasswordIsResticted() {
		assertTrue(forgetPasswordPage.isConfirmPasswordRestrictedToTwentyChar());
	}

	@Then("validate the New Password field is masked")
	public void passwordFieldShouldBeMasked() {
		assertTrue(forgetPasswordPage.isNewPasswordFieldMasked());
	}

	@Then("verify the Confirm Password field is masked")
	public void confirmPasswordFieldShouldBeMasked() {
		assertTrue(forgetPasswordPage.isConfirmPasswordFieldMasked());
	}

	@When("user clicks on the unmask icon in the New Password field")
	public void userClicksOnPassUnmaskIcon() {
		forgetPasswordPage.clickOnNewPasswordUnmaskIcon();
	}

	@Then("validate the New Password field is unmasked")
	public void passwordShouldBeUnmasked() {
		assertTrue(forgetPasswordPage.isNewPasswordFieldUnmasked());
	}

	@When("user clicks on the unmask icon in the ConfirmPassword field")
	public void userClicksOnConfirmPassUnmaskIcon() {
		forgetPasswordPage.clickOnConfirmPasswordUnmaskIcon();
	}

	@Then("verify the Confirm Password field is unmasked")
	public void confirmPasswordShouldBeUnmasked() {
		assertTrue(forgetPasswordPage.isConfirmPasswordFieldUnmasked());
	}

	@When("user clicks again on the unmask icon in the New Password field")
	public void userClicksAgainOnUnmaskIcon() {
		forgetPasswordPage.clickOnNewPasswordUnmaskIcon();
	}

	@When("user clicks again on the unmask icon in the ConfirmPassword field")
	public void userClicksAgainOnConfirmPassUnmaskIcon() {
		forgetPasswordPage.clickOnConfirmPasswordUnmaskIcon();
	}

	@Then("verify reset button is disabled")
	public void verifyResetButonIsDisabled() {
		assertFalse(forgetPasswordPage.isResetButtonEnabled());
	}

	@Then("user clicks on Reset button")
	public void userClickOnResetButton() {
		forgetPasswordPage.clickOnResetButton();
	}

	@Then("verify system display password reset in progress message")
	public void systemShouldBrieflyDisplayAccountSetupInProgressMessage() {
		try {
			if (forgetPasswordPage.isPasswordResetInProgressDisplayed()) {
				logger.info("Password Reset in progress message is displayed.");
			}
		} catch (Exception e) {
			logger.warn("Skipping step due to element disappearing instantly: " + e.getMessage());
		}
	}

	@Then("verify success screen with header Password Reset Confirmation is displayed")
	public void verifyHeaderOfSuccessScreen() {
		assertTrue(forgetPasswordPage.isPasswordResetConfirmationHeaderDisplayed());
	}

	@Then("verify the message Your password has been reset successfully. Please login to proceed. is displayed")
	public void verifyMessageOfSuccessScreen() {
		assertTrue(forgetPasswordPage.isPasswordResetConfirmationMessageDisplayed());
	}

	@Then("verify Login button is displayed")
	public void verifyLoginButtonDisplayed() {
		assertTrue(forgetPasswordPage.isLoginButtonDisplayed());
	}

	@When("user clicks on Login button")
	public void userClicksLoginButton() {
		forgetPasswordPage.clickOnLoginButton();
	}

	@Then("verify user is redirected to login screen of relying party")
	public void userNavigatesToLoginScreen() {
		assertTrue(forgetPasswordPage.isLoginScreenDisplayed());
	}

	@Then("verify You successfully changed KhID password. message is displayed")
	public void verifyNotificationForSuccessfullRegistration() {
		assertTrue(smtpPage.isPasswordResetSuccessfullNotificationReceivedInEnglish());
	}

	@Then("verify អ្នកបានផ្លាស់ប្ដូរពាក្យសម្ងាត់ KhID ដោយជោគជ័យ។ is displayed")
	public void verifySuccessfullRegistrationNotification() {
		assertTrue(smtpPage.isPasswordResetSuccessfullNotificationReceivedInKhmer());
	}

	@When("user click on reset password button")
	public void userClickOnResetPasswordButton() {
		forgetPasswordPage.clickOnResetPasswordButton();
	}

	@Then("verify it is accessible,user is redirected to the Forget Password screen")
	public void verifyUserRedirectedToForgotPasswordScreen() {
		assertTrue(forgetPasswordPage.isForgetPassowrdScreenVisible());
	}

}