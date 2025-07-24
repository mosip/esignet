package pages;

import base.BasePage;
import utils.EsignetConfigManager;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.time.Duration;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;

public class RegistrationPage extends BasePage {

	public RegistrationPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(xpath = "//div[@class='grow px-3 text-center font-semibold tracking-normal xs:px-2']")
	WebElement registrationScreen;

	@FindBy(xpath = "//div[@class='grow px-3 text-center font-semibold tracking-normal xs:px-2']")
	WebElement headerInRegistrationPage;

	@FindBy(id = "phone_input")
	WebElement enterMobileNumberTextBox;

	@FindBy(id = "continue-button")
	WebElement continueButton;

	@FindBy(id = "back-button")
	WebElement backButton;

	@FindBy(id = "language-select-button")
	WebElement languageSelection;

	@FindBy(xpath = "//*[@id='root']/div/div/div/footer/span")
	WebElement footerText;

	@FindBy(xpath = "//img[@class='footer-brand-logo']")
	WebElement footerLogo;

	@FindBy(xpath = "//div[@id=':r4:-form-item']/span")
	WebElement prefilledCountryCode;

	@FindBy(id = "phone_input")
	WebElement helpTextInTextBox;

	@FindBy(id = ":r4:-form-item-message")
	WebElement numberCannotStartWithZeroErrorMessage;

	@FindBy(id = ":r4:-form-item-message")
	List<WebElement> enterValidUserNameError;

	@FindBy(id = "login-header")
	WebElement loginPageHeader;

	@FindBy(xpath = "//div[@class='w-full text-center text-[22px] font-semibold']")
	WebElement otpPage;

	@FindBy(xpath = "//div[@class='text-muted-neutral-gray']")
	WebElement otpPageDescription;

	@FindBy(xpath = "//div[@class='pincode-input-container']/input")
	List<WebElement> otpInputFields;

	@FindBy(xpath = "//div[@class='pincode-input-container']")
	WebElement otpInputField;

	@FindBy(id = "verify-otp-button")
	WebElement verifyOtpButton;

	@FindBy(xpath = "//div[@class='flex gap-x-1 text-center']/span")
	WebElement otpCountDownTimer;

	@FindBy(id = "resend-otp-button")
	WebElement resendOtpButton;

	@FindBy(xpath = "//div[@class='w-max rounded-md bg-[#FFF7E5] p-2 px-8 text-center text-sm font-semibold text-[#8B6105]']")
	WebElement remainingAttemptsMeassage;

	@FindBy(xpath = "//p[@class='truncate text-xs text-destructive']")
	WebElement otpExpiredError;

	@FindBy(xpath = "//p[@class='truncate text-xs text-destructive']")
	WebElement incorrectOtpError;

	@FindBy(id = "success_message_icon")
	WebElement successMessagePage;

	@FindBy(xpath = "//h1[@class='text-center text-2xl font-semibold']")
	WebElement successHeader;

	@FindBy(xpath = "//p[@class='text-center text-gray-500']")
	WebElement successMessage;

	@FindBy(id = "mobile-number-verified-continue-button")
	WebElement continueButtonInSuccessPage;

	@FindBy(xpath = "//h1[@class='text-center text-2xl font-semibold']")
	WebElement failureHeader;

	@FindBy(xpath = "//p[@class='text-center text-gray-500']")
	WebElement failureMessage;

	@FindBy(id = "signup-failed-okay-button")
	WebElement loginButtonInSignUpFailedScreen;

	@FindBy(id = "success-continue-button")
	WebElement loginButtonInSuccessScreen;

	@FindBy(id = "login-header")
	WebElement loginScreen;

	@FindBy(id = "cross_icon")
	WebElement errorCloseIcon;

	@FindBy(xpath = "//h3[@class='text-3xl font-medium leading-none tracking-tight']")
	WebElement setupAccountHeader;

	@FindBy(xpath = "//div[@class='text-center text-gray-500']")
	WebElement setupAccountDescription;

	@FindBy(id = "username")
	WebElement usernameField;

	@FindBy(id = "fullNameInKhmer")
	WebElement fullNameInKhmerField;

	@FindBy(id = "password")
	WebElement passwordField;

	@FindBy(id = "confirmPassword")
	WebElement confirmPasswordField;

	@FindBy(id = "password-toggle-password")
	WebElement passwordToggleIcon;

	@FindBy(id = "confirmPassword-toggle-password")
	WebElement confirmPasswordToggleIcon;

	@FindBy(id = "password-info-icon")
	WebElement passwordInfoIcon;

	@FindBy(id = "consent-button")
	WebElement termsAndConditionsCheckbox;

	@FindBy(id = "account-setup-submit-button")
	WebElement setupContinueButton;

	@FindBy(id = "km_language")
	WebElement khmerLanguageSelection;

	@FindBy(id = ":r7:-form-item-message")
	WebElement fullNameHasToBeInKhmerOnlyError;

	@FindBy(id = "en_language")
	WebElement englishLanguageSelection;

	@FindBy(id = ":r7:-form-item-message")
	WebElement pleaseEnterValidNameError;

	@FindBy(id = ":ra:-form-item-message")
	WebElement passwordFieldError;

	@FindBy(xpath = "//div[@class='text-center text-gray-500']")
	WebElement descriptionSetupAccountPage;

	@FindBy(id = ":rd:-form-item-message")
	WebElement confirmPasswordFieldError;

	@FindBy(id = "fullName-info-icon")
	WebElement fullNameInKhmerInfoIcon;

	@FindBy(id = "radix-:rb:")
	WebElement passwordFieldTooltipText;

	@FindBy(id = "radix-:r8:")
	WebElement fullNameInKhmerTooltipText;

	@FindBy(xpath = "//label[@class='text-sm leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 font-medium']")
	WebElement messageToAcceptTermsAndCondition;

	@FindBy(xpath = "//span[@class='text-primary underline hover:cursor-pointer']")
	private WebElement termsAndConditionsLink;

	@FindBy(xpath = "//h3[@class='text-xl font-semibold text-gray-900 dark:text-gray-900']")
	private WebElement termsAndConditionsPopUp;

	@FindBy(id = "cross_icon")
	WebElement popupWindowCloseIcon;

	@FindBy(xpath = "//span[@class='text-primary underline hover:cursor-pointer']")
	private WebElement privacyPolicyLink;

	@FindBy(xpath = "//h3[@class='text-xl font-semibold text-gray-900 dark:text-gray-900']")
	private WebElement privacyPolicyPopUp;

	@FindBy(xpath = "//div[@class='text-center text-lg font-semibold']")
	private WebElement accountCreatedSuccessfullyMessage;

	@FindBy(id = "username")
	WebElement screenInEnglishLanguage;

	public boolean isRegistrationScreenDisplayed() {
		return isElementVisible(registrationScreen);
	}

	public boolean isHeaderInRegistrationPageDisplayed() {
		return isElementVisible(headerInRegistrationPage);
	}

	public boolean isEnterMobileNumberTextBoxDisplayed() {
		return isElementVisible(enterMobileNumberTextBox);
	}

	public boolean isContinueButtonVisible() {
		return isElementVisible(continueButton);
	}

	public boolean isBackOptionAvailable() {
		return isElementVisible(backButton);
	}

	public boolean isLanguageSelectionVisible() {
		return isElementVisible(languageSelection);
	}

	public boolean isFooterTextDisplayed() {
		return isElementVisible(footerText);
	}

	public boolean isFooterLogoDisplayed() {
		return isElementVisible(footerLogo);
	}

	public boolean isTextBoxPrefilledWithCountryCode() {
		return isElementVisible(prefilledCountryCode);
	}

	public boolean isHelpTextInMobileNumberTextBoxDisplayed(String expectedText) {
		String placeholder = getElementAttribute(helpTextInTextBox, "placeholder");
		return placeholder != null && placeholder.equals(expectedText);
	}

	private String lastEnteredMobileNumber;

	public String getLastEnteredMobileNumber() {
		return lastEnteredMobileNumber;
	}

	public boolean isPlaceholderGone() {
		String value = getElementValue(enterMobileNumberTextBox);
		return value != null && !value.isEmpty();
	}

	public void enterMobileNumber(String number) {
		enterMobileNumberTextBox.clear();
		enterText(enterMobileNumberTextBox, number);
		enterMobileNumberTextBox.clear();
		lastEnteredMobileNumber = "+855 " + number;
	}

	public void enterOtp(String otp) {
		for (WebElement field : otpInputFields) {
			field.click();
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
			field.sendKeys(Keys.BACK_SPACE);
		}
		for (int i = 0; i < otp.length(); i++) {
			WebElement field = otpInputFields.get(i);
			field.click();
			field.sendKeys(String.valueOf(otp.charAt(i)));
		}
	}

	public void clickOnOutsideMobileField() {
		clickOnElement(headerInRegistrationPage);
	}

	public boolean isContinueButtonEnabled() {
		return isButtonEnabled(continueButton);
	}

	public boolean isErrorMessageDisplayed() {
		return !enterValidUserNameError.isEmpty() && enterValidUserNameError.get(0).isDisplayed();
	}

	public void verifyErrorIsGoneAfterClose() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
		wait.until(ExpectedConditions.invisibilityOf(otpExpiredError));
	}

	public void verifyErrorMessageDisappesAfterTenSeconds() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(12));
		wait.until(ExpectedConditions.invisibilityOf(incorrectOtpError));
	}

	public void verifyErrorMessageDisappesAsUserStartsTyping() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
		wait.until(ExpectedConditions.invisibilityOf(incorrectOtpError));
	}

	public void clickOnContinueButton() {
		clickOnElement(continueButton);
	}

	public boolean isZeroErrorMessageDisplayed() {
		return isElementVisible(numberCannotStartWithZeroErrorMessage);
	}

	public boolean isNumberRestrictedToNineDigits() {
		String value = getElementValue(enterMobileNumberTextBox);
		return value != null && value.length() == 9;
	}

	public boolean isMobileFieldEmptyOrUnchanged() {
		String value = getElementValue(enterMobileNumberTextBox);
		return value == null || value.isEmpty();
	}

	public boolean isMobileFieldContainingOnlyDigits() {
		String value = getElementValue(enterMobileNumberTextBox);
		return value != null && value.matches("\\d+");
	}

	public void clickOnNavigateBackButton() {
		clickOnElement(backButton);
	}

	public boolean isPreviousScreenVisible() {
		return isElementVisible(loginPageHeader);
	}

	public boolean isEnterOtpPageDisplayed() {
		return isElementVisible(otpPage);
	}

	public boolean isOtpPageHeaderDisplayed() {
		return isElementVisible(otpPage);
	}

	public boolean isOtpPageDescriptionDisplayed() {
		return isElementVisible(otpPageDescription);
	}

	public boolean isOtpInputFieldVisible() {
		return isElementVisible(otpInputField);
	}

	public boolean isVerifyOtpButtonVisible() {
		return isElementVisible(verifyOtpButton);
	}

	public boolean isCountdownTimerDisplayed() {
		return isElementVisible(otpCountDownTimer);
	}

	public boolean isResendOtpOptionVisible() {
		return isElementVisible(resendOtpButton);
	}

	public boolean isBackToEditMobileNumberOptionVisible() {
		return isElementVisible(backButton);
	}

	public void waitUntilOtpTimerExpires() {
		int otpExpiry = Integer.parseInt(EsignetConfigManager.getProperty("otp.expiry.seconds", ""));
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(otpExpiry));
		wait.until(ExpectedConditions.textToBePresentInElement(otpCountDownTimer, "00:00"));
		wait.until(ExpectedConditions.elementToBeClickable(resendOtpButton));
		resendOtpButton.click();
	}

	public void waitUntilOtpExpires() {
		int otpExpiry = Integer.parseInt(EsignetConfigManager.getProperty("otp.expiry.seconds", ""));
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(otpExpiry));
		wait.until(ExpectedConditions.textToBePresentInElement(otpCountDownTimer, "00:00"));
	}

	public boolean isResendOtpButtonEnabled() {
		return isButtonEnabled(resendOtpButton);
	}

	public void clickOnResendOtpButton() {
		clickOnElement(resendOtpButton);
	}

	public boolean isOtpTimerRestarted() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		return wait.until(driver -> {
			String value = otpCountDownTimer.getText();
			return value.startsWith("01");
		});
	}

	public String getRemainingAttemptsText() {
		return remainingAttemptsMeassage.getText().trim();
	}

	public void clickOnVerifyOtpButton() {
		clickOnElement(verifyOtpButton);
	}

	public boolean isOtpExpiredMessageDisplayed() {
		return isElementVisible(otpExpiredError);
	}

	public boolean isIncorrectOtpErrorDisplayed() {
		return isElementVisible(incorrectOtpError);
	}

	public boolean isOtpFieldEmptyOrUnchanged() {
		String value = getElementValue(otpInputField);
		return value == null || value.isEmpty();
	}

	public boolean isOtpFieldEmptyfterAlphabetEntry() {
		String value = getElementValue(otpInputField);
		return value == null || value.isEmpty();
	}

	public boolean isOtpFieldsNumericOnly() {
		for (WebElement field : otpInputFields) {
			String value = getElementValue(field);
			if (value != null && !value.matches("\\d*")) {
				return false;
			}
		}
		return true;
	}

	public boolean isOtpMasked() {
		for (WebElement field : otpInputFields) {
			if (!"password".equalsIgnoreCase(getElementAttribute(field, "type"))) {
				return false;
			}
		}
		return true;
	}

	public void waitForButtonToBecomeDisabled(WebElement button, int timeoutSeconds) {
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
				.until(ExpectedConditions.not(ExpectedConditions.elementToBeClickable(button)));
	}

	public WebElement getVerifyOtpButton() {
		return verifyOtpButton;
	}

	public boolean isVerifyOtpButtonEnabled() {
		return isButtonEnabled(verifyOtpButton);
	}

	public boolean isSuccessScreenDisplayed() {
		return isElementVisible(successMessagePage);
	}

	public boolean isSuccessHeaderDisplayed() {
		return isElementVisible(successHeader);
	}

	public boolean iSuccessMessageDisplayed() {
		return isElementVisible(successMessage);
	}

	public boolean isContinueButtonDisplayed() {
		return isElementVisible(continueButtonInSuccessPage);
	}

	public boolean isFailureHeaderDisplayed() {
		return isElementVisible(failureHeader);
	}

	public boolean isFailureMessageDisplayed() {
		return isElementVisible(failureMessage);
	}

	public boolean isLoginButtonVisible() {
		return isElementVisible(loginButtonInSignUpFailedScreen);
	}

	public void clickOnLoginButtonInSignUpFailedScreen() {
		clickOnElement(loginButtonInSignUpFailedScreen);
	}

	public void clickOnContinueButtonInSucessScreen() {
		clickOnElement(continueButtonInSuccessPage);
	}

	public void clickOnErrorCloseIcon() {
		clickOnElement(errorCloseIcon);
	}

	public boolean isSetupAccountHeaderVisible() {
		return isElementVisible(setupAccountHeader);
	}

	public boolean isSetupAccountDescriptionVisible() {
		return isElementVisible(setupAccountDescription);
	}

	public boolean isUsernameFieldVisible() {
		return isElementVisible(usernameField);
	}

	public boolean isFullNameInKhmerFieldVisible() {
		return isElementVisible(fullNameInKhmerField);
	}

	public boolean isPasswordFieldVisible() {
		return isElementVisible(passwordField);
	}

	public boolean isConfirmPasswordFieldVisible() {
		return isElementVisible(confirmPasswordField);
	}

	public boolean isPasswordToggleIconVisible() {
		return isElementVisible(passwordToggleIcon);
	}

	public boolean isPasswordPolicyIconVisible() {
		return isElementVisible(passwordInfoIcon);
	}

	public boolean isTermsCheckboxVisible() {
		return isElementVisible(termsAndConditionsCheckbox);
	}

	public boolean isSetupContinueButtonVisible() {
		return isElementVisible(setupContinueButton);
	}

	public String getUsernameFieldValue() {
		return getElementValue(usernameField);
	}

	public boolean isUsernameFieldReadOnly() {
		return getElementAttribute(usernameField, "readonly") != null;
	}

	public String getFullNameInKhmerPlaceholder() {
		return getElementAttribute(fullNameInKhmerField, "placeholder");
	}

	public void clickOnLanguageSelectionOption() {
		clickOnElement(languageSelection);
	}

	public void clickOnKhmerLanguage() {
		clickOnElement(khmerLanguageSelection);
	}

	public void enterName(String name) throws Exception {
		clearField(fullNameInKhmerField);
		enterTextJS(fullNameInKhmerField, name);
	}

	public void clickOnOutsideNameField() {
		clickOnElement(setupAccountHeader);
	}

	public boolean isFullNameHasToBeInKhmerErrorDisplayed() {
		return isElementVisible(fullNameHasToBeInKhmerOnlyError);
	}

	public boolean isFullNameInKhmerRestrictedToThirtyChars() {
		String value = getElementValue(fullNameInKhmerField);
		return value != null && value.length() <= 30;
	}

	public void clickOnEnglishLanguage() {
		clickOnElement(englishLanguageSelection);
	}

	public boolean isPleaseEnterValidUsernameErrorDisplayed() {
		return isElementVisible(pleaseEnterValidNameError);
	}

	public boolean isLanguageChanged() {
		return isElementVisible(setupAccountHeader);
	}

	public String getPasswordFieldPlaceholder() {
		return getElementAttribute(passwordField, "placeholder");
	}

	public void enterPassword(String password) {
		clearField(passwordField);
		enterText(passwordField, password);
	}

	public boolean isPasswordDoesNotMeetThePolicyErrorDisplayed() {
		return isElementVisible(passwordFieldError);
	}

	public void tabsOutOfField() {
		passwordField.sendKeys(Keys.TAB);
	}

	public void enterConfirmPassword(String confirmPassword) {
		clearField(confirmPasswordField);
		enterText(confirmPasswordField, confirmPassword);
	}

	public boolean isPasswordRestrictedToTwentyChars() {
		String value = getElementValue(passwordField);
		return value != null && value.length() <= 20;
	}

	public String getConfirmPasswordFieldPlaceholder() {
		return getElementAttribute(confirmPasswordField, "placeholder");
	}

	public boolean isPasswordAndConfirmPasswordDoesNotMatchErrorDisplayed() {
		return isElementVisible(confirmPasswordFieldError);
	}

	public boolean isConfirmPasswordRestrictedToTwentyChars() {
		String value = getElementValue(confirmPasswordField);
		return value != null && value.length() <= 20;
	}

	public boolean isPasswordFieldMasked() {
		return "password".equalsIgnoreCase(getElementAttribute(passwordField, "type"));
	}

	public boolean isConfirmPasswordFieldMasked() {
		return "password".equalsIgnoreCase(getElementAttribute(confirmPasswordField, "type"));
	}

	public void clickOnPasswordUnmaskIcon() {
		clickOnElement(passwordToggleIcon);
	}

	public boolean isPasswordFieldUnmasked() {
		return "text".equalsIgnoreCase(getElementAttribute(passwordField, "type"));
	}

	public void clickOnConfirmPasswordUnmaskIcon() {
		clickOnElement(confirmPasswordToggleIcon);
	}

	public boolean isConfirmPasswordFieldUnmasked() {
		return "text".equalsIgnoreCase(getElementAttribute(confirmPasswordField, "type"));
	}

	public void clickOnPasswordInfoIcon() {
		clickOnElement(passwordInfoIcon);
	}

	public boolean isPasswordTooltipMessageDisplayed() {
		return isElementVisible(passwordFieldTooltipText);
	}

	public void clickOnFullNameInKhmerInfoIcon() {
		clickOnElement(fullNameInKhmerInfoIcon);
	}

	public boolean isFullNameInKhmerTooltipMessage() {
		return isElementVisible(fullNameInKhmerTooltipText);
	}

	public void ensureTermsCheckboxIsUnchecked() {
		if (termsAndConditionsCheckbox.isSelected()) {
			termsAndConditionsCheckbox.click();
		}
	}

	public boolean isContinueButtonInSetupAccountPageEnabled() {
		return isButtonEnabled(setupContinueButton);
	}

	public boolean isTermsAndConditionsMessageDisplayed() {
		return isElementVisible(messageToAcceptTermsAndCondition);
	}

	public void clickOnTermsAndConditionLink() {
		clickOnElement(termsAndConditionsLink);
	}

	public boolean isTermsAndConditionsPopupDisplayed() {
		return isElementVisible(termsAndConditionsPopUp);
	}

	public void clickOnClosePopupIcon() {
		clickOnElement(popupWindowCloseIcon);
	}

	public boolean isSetupAccountPageVisible() {
		return isElementVisible(setupAccountHeader);
	}

	public void clickOnPrivacyPolicyLink() {
		clickOnElement(privacyPolicyLink);
	}

	public boolean isPrivacyPolicyPopupDisplayed() {
		return isElementVisible(privacyPolicyPopUp);
	}

	public void clearAllMandatoryFields() {
		fullNameInKhmerField.clear();
		passwordField.clear();
		confirmPasswordField.clear();
	}

	public void checkTermsAndConditions() {
		if (!termsAndConditionsCheckbox.isSelected()) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});",
					termsAndConditionsCheckbox);
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", termsAndConditionsCheckbox);
		}
	}

	public WebElement getTermsAndConditionsCheckbox() {
		return termsAndConditionsCheckbox;
	}

	public void clickOnSetupAccountContinueButton() {
		clickOnElement(setupContinueButton);
	}

	public boolean isScreenDisplayedInEnglishLang() {
		return isElementVisible(screenInEnglishLanguage);
	}

}
