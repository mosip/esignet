package pages;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;
import utils.EsignetConfigManager;

public class ForgetPasswordPage extends BasePage {

	public ForgetPasswordPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "login_with_pwd")
	WebElement loginWithPassword;

	@FindBy(id = "forgot-password-hyperlink")
	WebElement forgetPasswordLink;

	@FindBy(id = "sign-in-with-esignet")
	WebElement signInWithEsignet;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(xpath = "//span[@class='flex self-center border-r-[1px] border-input px-3 text-muted-foreground/60']")
	WebElement phonePrefix;

	@FindBy(id = "phone_input")
	WebElement phoneInput;

	@FindBy(xpath = "//span[text()='+855']")
	WebElement countryCodeSpan;

	@FindBy(xpath = "//div/p[@id=':r4:-form-item-message']")
	WebElement phoneErrorForInvalidValue;

	@FindBy(xpath = "//div[@class='text-center text-[26px] font-semibold tracking-normal']")
	WebElement forgetPasswordHeading;

	@FindBy(id = "keyboard_backspace_FILL0_wght400_GRAD0_opsz48")
	WebElement backButtonOnForgePassword;

	@FindBy(xpath = "//div[@class='text-center text-gray-500']")
	WebElement forgetPasswordSubHeadning;

	@FindBy(xpath = "//label[normalize-space(text())='Username']")
	WebElement userNameLabel;

	@FindBy(xpath = "//label[normalize-space(text())='Full Name in Khmer']")
	WebElement fullNameLabel;

	@FindBy(id = "continue-button")
	WebElement continueButton;

	@FindBy(id = "language-select-button")
	WebElement LangSelectionButton;

	@FindBy(xpath = "//footer//span[text()='Powered by']")
	WebElement poweredByText;

	@FindBy(xpath = "//footer//img[@class='footer-brand-logo' and @alt='eSignet Signup']")
	WebElement footerLogo;

	@FindBy(id = "fullname")
	WebElement fullNameInput;

	@FindBy(xpath = "//p[@id=':r5:-form-item-message']")
	WebElement fullNameError;

	@FindBy(xpath = "//div[contains(text(),'Please enter 6-digit OTP received on your number')]")
	WebElement otpInstructionText;

	@FindBy(xpath = "//div[contains(text(),'Enter OTP')]")
	WebElement enterOtpHeading;

	@FindBy(xpath = "//div[@class='font-medium text-muted-dark-gray']")
	WebElement partiallyMaskedPhone;

	@FindBy(xpath = "//div[contains(text(),'You can resend the OTP in')]")
	WebElement resendOtpCountdownText;

	@FindBy(id = "resend-otp-button")
	WebElement resendOtpButton;

	@FindBy(xpath = "//div[@class='font-medium text-muted-dark-gray']")
	private WebElement maskedPhone;

	@FindBy(xpath = "//div[@class='pincode-input-container']/input")
	List<WebElement> otpInputFields;

	@FindBy(xpath = "//p[@class='truncate text-xs text-destructive']")
	WebElement incorrectOtpError;

	@FindBy(xpath = "//p[@class='truncate text-xs text-destructive']")
	WebElement otpExpiredError;

	@FindBy(xpath = "//div[@class='pincode-input-container']")
	WebElement otpInputField;

	@FindBy(id = "back-button")
	WebElement backButton;

	@FindBy(xpath = "//div[contains(text(),'attempt') or contains(text(),'Attempt')]")
	private WebElement resendAttemptsText;

	@FindBy(xpath = "//div[contains(text(),'You can resend the OTP in')]/span")
	private WebElement otpCountdownTimer;

	@FindBy(xpath = "//div[text()='Reset Password' and contains(@class, 'text-center') and contains(@class, 'font-semibold') and contains(@class, 'tracking-normal')]")
	private WebElement resetPasswordHeader;

	@FindBy(xpath = "//div[text()='To set the new password, please enter the new password and re-enter the password to confirm.']")
	private WebElement passwordInstructionMessage;

	@FindBy(xpath = "//label[text()='New Password']")
	private WebElement newPasswordLabel;

	@FindBy(xpath = "//label[text()='Confirm New Password']")
	private WebElement confirmnNewPasswordLabel;

	@FindBy(id = "newPassword-info-icon")
	private WebElement newPasswordInfoIcon;

	@FindBy(id = "radix-:r8:")
	private WebElement passwordPolicyTooltip;

	@FindBy(id = "newPassword")
	private WebElement newPasswordInput;

	@FindBy(id = "confirmNewPassword")
	private WebElement confirmNewPasswordInput;

	@FindBy(id = "alert-action-button")
	private WebElement retryButton;

	@FindBy(xpath = "//h2[@class='text-lg font-semibold flex flex-col items-center justify-center gap-y-4']")
	private WebElement invalidErrorHeader;

	@FindBy(xpath = "//p[@class='text-sm text-center text-muted-dark-gray']")
	private WebElement errorMessage;

	@FindBy(id = ":r7:-form-item-message")
	private WebElement passwordErrorMessage;

	@FindBy(id = "newPassword-toggle-password")
	private WebElement newPasswordToggleIcon;

	@FindBy(id = ":ra:-form-item-message")
	private WebElement confirmPasswordErrorMessage;

	@FindBy(id = "confirmNewPassword-toggle-password")
	private WebElement confirmPasswordToggleIcon;

	@FindBy(id = "reset-password-button")
	private WebElement resetButton;

	@FindBy(xpath = "//h1[@class='text-center text-2xl font-semibold']")
	WebElement passwordResetInProgress;

	@FindBy(xpath = "//p[@class='text-center text-muted-neutral-gray']")
	WebElement pleaseWaitMessageInResetPassword;

	@FindBy(xpath = "//div[@class='text-center text-lg font-semibold']")
	private WebElement passwordResetConfirmationHeader;

	@FindBy(xpath = "//p[@class='text-center text-muted-neutral-gray']")
	private WebElement passwordResetConfirmationMessage;

	@FindBy(id = "success-continue-button")
	private WebElement loginButtonInSuccessScreen;

	@FindBy(id = "login-header")
	private WebElement loginScreen;

	@FindBy(id = "reset-password-button")
	WebElement resetPasswordButton;

	public void clickOnLoginWithPassword() {
		clickOnElement(loginWithPassword);
	}

	public boolean isforgetPasswordLinkDisplayed() {
		return isElementVisible(forgetPasswordLink);
	}

	public void clickOnForgetPasswordLink() {
		clickOnElement(forgetPasswordLink);
	}

	public void clickOnSignInWIthEsignet() {
		clickOnElement(signInWithEsignet);
	}

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo);
	}

	public boolean isRedirectedToResetPasswordPage() {
		String currentUrl = driver.getCurrentUrl();
		return currentUrl.contains(getSignupUrl() + "reset-password");
	}

	public boolean isphonePrefixDisplayed() {
		return isElementVisible(phonePrefix);
	}

	public boolean isWaterMarkDisplayed() {
		return getElementAttribute(phoneInput, "placeholder").equals("Enter 8-9 digit mobile number");
	}

	public boolean isCountryCodeNonEditable() {
		return isElementVisible(countryCodeSpan) && !getElementTagName(countryCodeSpan).equalsIgnoreCase("input");
	}

	public boolean isPhoneErrorVisible() {
		return isElementVisible(phoneErrorForInvalidValue);
	}

	public void enterPhoneNumber(String number) {
		phoneInput.clear();
		enterText(phoneInput, number);
	}

	public void triggerPhoneValidation() {
		clickOnElement(countryCodeSpan);
	}

	public boolean isForgetPasswordHeadningVisible() {
		return isElementVisible(forgetPasswordHeading);
	}

	public boolean isBackButtonOnForgePasswordVisible() {
		return isElementVisible(backButtonOnForgePassword);
	}

	public boolean isForgetPasswordSubHeadningVisible() {
		return isElementVisible(forgetPasswordSubHeadning);
	}

	public boolean isUserNameLabelVisible() {
		return isElementVisible(userNameLabel);
	}

	public boolean isFullNameLabelVisible() {
		return isElementVisible(fullNameLabel);
	}

	public boolean isContinueButtonVisible() {
		return isElementVisible(continueButton);
	}

	public boolean isLangSelectionButtonVisible() {
		return isElementVisible(LangSelectionButton);
	}

	public boolean isFooterPoweredByVisible() {
		return isElementVisible(poweredByText) && isElementVisible(footerLogo);
	}

	public String getEnteredPhoneNumber() {
		return getElementAttribute(phoneInput, "value");
	}

	public boolean isContinueButtonDisabled() {
		return !isButtonEnabled(continueButton);
	}

	public void enterFullName(String name) {
		enterText(fullNameInput, name);
	}

	public boolean isFullNameErrorVisible() {
		try {
			return isElementVisible(fullNameError);
		} catch (NoSuchElementException e) {
			return false;
		}
	}

	public boolean isFullNameErrorPresent() {
		return driver.findElements(By.xpath("//p[@id=':r5:-form-item-message']")).size() > 0;
	}

	public String getEnteredFullName() {
		return getElementAttribute(fullNameInput, "value");
	}

	public boolean isContinueButtonEnabled() {
		return isButtonEnabled(continueButton);
	}

	public void clockOnContinueButton() {
		clickOnElement(continueButton);
	}

	public void clickOnBackButtonOnForgetPassword() {
		clickOnElement(backButtonOnForgePassword);
	}

	public boolean isRedirectedToLoginPage() {
		return isElementVisible(loginWithPassword);
	}

	public boolean isOtpInstructionVisible() {
		return isElementVisible(otpInstructionText);
	}

	public boolean isEnterOtpHeadingVisible() {
		return isElementVisible(enterOtpHeading);
	}

	public boolean isResendOtpCountdownVisible() {
		return isElementVisible(resendOtpCountdownText);
	}

	public boolean isResendOtpButtonVisible() {
		return isElementVisible(resendOtpButton);
	}

	public String getMaskedPhoneText() {
		return getText(partiallyMaskedPhone);
	}

	public boolean isMaskedPhoneVisible() {
		return isElementVisible(maskedPhone);
	}

	public void waitUntilOtpExpire() {
		int otpExpiry = Integer.parseInt(EsignetConfigManager.getProperty("otp.expiry.seconds", ""));
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(otpExpiry));
		wait.until(ExpectedConditions.textToBePresentInElement(otpCountdownTimer, "00:00"));
	}

	public boolean isInputRestrictedToNineDigits() {
		String value = getElementValue(phoneInput);
		return value != null && value.length() == 9;
	}

	public void enterOtp(String otp) {
		for (int i = 0; i < otp.length(); i++) {
			WebElement field = otpInputFields.get(i);
			field.click();
			field.sendKeys(String.valueOf(otp.charAt(i)));
		}
	}

	public boolean isResendOtpButtonEnabled() {
		return isButtonEnabled(resendOtpButton);
	}

	public void clickOnResendOtp() {
		clickOnElement(resendOtpButton);
	}

	public String getOtpResendAttemptsText() {
		return getText(resendAttemptsText);
	}

	public void clickOnNavigateBackButton() {
		clickOnElement(backButton);
	}

	public boolean isForgetPassowrdScreenVisible() {
		return isElementVisible(forgetPasswordHeading);
	}

	public boolean isResetPasswordScreenVisible() {
		return isElementVisible(resetPasswordHeader);
	}

	public boolean isResetPasswordHeaderVisible() {
		return isElementVisible(resetPasswordHeader);
	}

	public boolean isPasswordInstructionMessageVisible() {
		return isElementVisible(passwordInstructionMessage);
	}

	public boolean isNewPasswordLabelVisible() {
		return isElementVisible(newPasswordLabel);
	}

	public boolean isConfirmNewPasswordLabelVisible() {
		return isElementVisible(confirmnNewPasswordLabel);
	}

	public boolean isNewPasswordInputTextboxVisible() {
		return isElementVisible(newPasswordInput);
	}

	public boolean isConfirmNewPasswordInputTextboxVisible() {
		return isElementVisible(confirmNewPasswordInput);
	}

	public boolean isNewPasswordInfoIconVisible() {
		return isElementVisible(newPasswordInfoIcon);
	}

	public void clickOnNewPasswordInfoIcon() {
		clickOnElement(newPasswordInfoIcon);
	}

	public boolean isPasswordPolicyTooltipVisible() {
		return isElementVisible(passwordPolicyTooltip);
	}

	public String getNewPasswordFieldPlaceholder() {
		return getElementAttribute(newPasswordInput, "placeholder");
	}

	public String getConfirmNewPasswordFieldPlaceholder() {
		return getElementAttribute(confirmNewPasswordInput, "placeholder");
	}

	public boolean isErrorHeaderInvalidVisible() {
		return isElementVisible(invalidErrorHeader);
	}

	public boolean isErrorMessageVisible() {
		return isElementVisible(errorMessage);
	}

	public boolean isRetryButtonVisible() {
		return isElementVisible(retryButton);
	}

	public void clickOnRetryButton() {
		clickOnElement(retryButton);
	}

	public void enterNewPassword(String newPassword) {
		clearField(newPasswordInput);
		enterText(newPasswordInput, newPassword);
	}

	public void enterConfirmPassword(String confirmPassword) {
		clearField(confirmNewPasswordInput);
		enterText(confirmNewPasswordInput, confirmPassword);
	}

	public void clickOutsidePasswordField() {
		clickOnElement(newPasswordToggleIcon);
	}

	public boolean isPasswordErrorDisplayed() {
		return isElementVisible(passwordErrorMessage);
	}

	public boolean isPasswordRestrictedToTwentyChar() {
		String value = getElementValue(newPasswordInput);
		return value != null && value.length() <= 20;
	}

	public boolean isconfirmPasswordErrorDisplayed() {
		return isElementVisible(confirmPasswordErrorMessage);
	}

	public boolean isConfirmPasswordRestrictedToTwentyChar() {
		String value = getElementValue(confirmNewPasswordInput);
		return value != null && value.length() <= 20;
	}

	public boolean isNewPasswordFieldMasked() {
		return "password".equalsIgnoreCase(getElementAttribute(newPasswordInput, "type"));
	}

	public boolean isConfirmPasswordFieldMasked() {
		return "password".equalsIgnoreCase(getElementAttribute(confirmNewPasswordInput, "type"));
	}

	public void clickOnNewPasswordUnmaskIcon() {
		clickOnElement(newPasswordToggleIcon);
	}

	public boolean isNewPasswordFieldUnmasked() {
		return "text".equalsIgnoreCase(getElementAttribute(newPasswordInput, "type"));
	}

	public void clickOnConfirmPasswordUnmaskIcon() {
		clickOnElement(confirmPasswordToggleIcon);
	}

	public boolean isConfirmPasswordFieldUnmasked() {
		return "text".equalsIgnoreCase(getElementAttribute(confirmNewPasswordInput, "type"));
	}

	public boolean isResetButtonEnabled() {
		return isButtonEnabled(resetButton);
	}

	public void clickOnResetButton() {
		clickOnElement(resetButton);
	}

	public boolean isPasswordResetInProgressDisplayed() {
		return passwordResetInProgress.isDisplayed() && pleaseWaitMessageInResetPassword.isDisplayed();
	}

	public boolean isPasswordResetConfirmationHeaderDisplayed() {
		return isButtonEnabled(passwordResetConfirmationHeader);
	}

	public boolean isPasswordResetConfirmationMessageDisplayed() {
		return isElementVisible(passwordResetConfirmationMessage);
	}

	public boolean isLoginButtonDisplayed() {
		return isButtonEnabled(loginButtonInSuccessScreen);
	}

	public void clickOnLoginButton() {
		clickOnElement(loginButtonInSuccessScreen);
	}

	public boolean isLoginScreenDisplayed() {
		return isButtonEnabled(loginScreen);
	}

	public void clickOnResetPasswordButton() {
		clickOnElement(resetPasswordButton);
	}
}