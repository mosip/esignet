package pages;

import base.BasePage;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.time.Duration;

import org.openqa.selenium.By;
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

	@FindBy(xpath = "//*[@id=\"root\"]/div/div/div/footer/span")
	WebElement footerText;

	@FindBy(xpath = "//img[@class='footer-brand-logo']")
	WebElement footerLogo;

	@FindBy(xpath = "//div[@id=\":r4:-form-item\"]/span")
	WebElement prefilledCountryCode;

	@FindBy(id = "phone_input")
	WebElement helpTextInTextBox;

	@FindBy(xpath = "//p[@class='rounded-b-lg bg-destructive-foreground p-2 text-sm font-medium text-destructive w-full']")
	WebElement numberCannotStartWithZeroErrorMessage;

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
		String placeholder = helpTextInTextBox.getAttribute("placeholder");
		return placeholder != null && placeholder.equals(expectedText);
	}

	private String lastEnteredMobileNumber;

	public String getLastEnteredMobileNumber() {
		return lastEnteredMobileNumber;
	}

	public boolean isPlaceholderGone() {
		String value = enterMobileNumberTextBox.getAttribute("value");
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
		List<WebElement> elements = driver.findElements(By.xpath(
				"//p[@class='rounded-b-lg bg-destructive-foreground p-2 text-sm font-medium text-destructive w-full']"));
		return !elements.isEmpty() && elements.get(0).isDisplayed();
	}

	public void waitForErrorMessageToDisappear() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(12));
		wait.until(ExpectedConditions.invisibilityOf(incorrectOtpError));
	}

	public void clickOnContinueButton() {
		clickOnElement(continueButton);
	}

	public boolean isZeroErrorMessageDisplayed() {
		return isElementVisible(numberCannotStartWithZeroErrorMessage);
	}

	public boolean isNumberRestrictedToNineDigits() {
		String value = enterMobileNumberTextBox.getAttribute("value");
		return value != null && value.length() == 9;
	}

	public boolean isMobileFieldEmptyOrUnchanged() {
		String value = enterMobileNumberTextBox.getAttribute("value");
		return value == null || value.isEmpty();
	}

	public boolean isMobileFieldContainingOnlyDigits() {
		String value = enterMobileNumberTextBox.getAttribute("value");
		return value != null && value.matches("\\d+");
	}

	public void clickOnNavigateBackButton() {
		clickOnElement(backButton);
	}

	public boolean isPreviousScreenVisible() {
		return isElementVisible(loginPageHeader);
	}

	public void browserBackButton() {
		driver.navigate().back();
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
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(90));
		wait.until(ExpectedConditions.textToBePresentInElement(otpCountDownTimer, "00:00"));
		wait.until(ExpectedConditions.elementToBeClickable(resendOtpButton));
		resendOtpButton.click();
	}

	public void waitUntilOtpExpires() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(90));
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
		String value = otpInputField.getAttribute("value");
		return value == null || value.isEmpty();
	}

	public boolean isOtpFieldEmptyfterAlphabetEntry() {
		String value = otpInputField.getAttribute("value");
		return value == null || value.isEmpty();
	}

	public boolean isOtpFieldsNumericOnly() {
		for (WebElement field : otpInputFields) {
			String value = field.getAttribute("value");
			if (value != null && !value.matches("\\d*")) {
				return false;
			}
		}
		return true;
	}

	public boolean isOtpMasked() {
		for (WebElement field : otpInputFields) {
			if (!"password".equalsIgnoreCase(field.getAttribute("type"))) {
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

}
