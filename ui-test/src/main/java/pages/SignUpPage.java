package pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;
import utils.EsignetConfigManager;

public class SignUpPage extends BasePage {

	public SignUpPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "signup-url-button")
	WebElement signUp;
	
	@FindBy(id = "language-select-button")
	WebElement languageDropdownButton;
	
	@FindBy(id = "register-button")
	WebElement registerButton;
	
	@FindBy(id = "phone_input")
	WebElement mobileNumberInput;
	
	@FindBy(id = "continue-button")
	WebElement continueButton;

	@FindBy(xpath = "//div[@class='pincode-input-container']/input")
	List<WebElement> otpInputFields;

	@FindBy(id = "verify-otp-button")
	WebElement verifyOtpButton;

	@FindBy(id = "mobile-number-verified-continue-button")
	WebElement continueButtonInSuccessPage;

	@FindBy(id = "fullName_eng")
	WebElement fullNameInEnglishField;
	
	@FindBy(id = "fullName_khm")
	WebElement fullNameKhmerField;

	@FindBy(id = "password")
	WebElement passwordField;

	@FindBy(id = "password_confirm")
	WebElement confirmPasswordField;

	@FindBy(id = "consent")
	WebElement termsAndConditionsCheckbox;

	@FindBy(xpath = "//button[@class='form-button']")
	WebElement setupContinueButton;
	
	@FindBy(xpath = "//div[@class='text-center text-lg font-semibold']")
	WebElement accountCreatedSuccessMessage;
	
	@FindBy(xpath = "//div[@class='alternate-icon-div']")
	WebElement uploadPhoto;
	
	@FindBy(id = "individualBiometrics-capture-button")
	WebElement captureButton;

	public void clickOnSignUp() {
		clickOnElement(signUp);
	}
	
	public void clickOnLanguageDropdown() {
		clickOnElement(languageDropdownButton);
	}
	
	public void navigateToSignupPortal() {
		driver.get(EsignetConfigManager.getSignuplUrl());
	}
	
	public void clickOnRegisterButton() {
		clickOnElement(registerButton);
	}
	
	public void enterMobileNumber(String number) {
		enterText(mobileNumberInput, number);
	}
	
	public void clickOnContinueButton() {
		clickOnElement(continueButton);
	}

	public void enterOtp(String otp) {
		for (int i = 0; i < otp.length(); i++) {
			WebElement field = otpInputFields.get(i);
			field.click();
			field.sendKeys(String.valueOf(otp.charAt(i)));
		}
	}

	public void clickOnVerifyOtpButton() {
		clickOnElement(verifyOtpButton);
	}

	public void clickOnContinueButtonInSucessScreen() {
		clickOnElement(continueButtonInSuccessPage);
	}

	public void enterFullNameInEnglish(String name) {
		enterTextJS(fullNameInEnglishField, name);
	}

	public void enterFullNameInKhmer(String name) {
		enterTextJS(fullNameKhmerField, name);
	}
	
	public void enterPassword(String password) {
		passwordField.clear();
		enterText(passwordField, password);
	}

	public void enterConfirmPassword(String confirmPassword) {
		confirmPasswordField.clear();
		enterText(confirmPasswordField, confirmPassword);
	}

	public void checkTermsAndConditions() {
		if (!termsAndConditionsCheckbox.isSelected()) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});",
					termsAndConditionsCheckbox);
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", termsAndConditionsCheckbox);
		}
	}

	public void clickOnSetupAccountContinueButton() {
		clickOnElement(setupContinueButton);
	}
	
	public boolean isAccountCreatedSuccessMessageDisplayed() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
	    wait.until(ExpectedConditions.visibilityOf(accountCreatedSuccessMessage));
		return isElementVisible(accountCreatedSuccessMessage);
	}
	
	public void clickOnUploadPhoto() {
		clickOnElement(uploadPhoto);
	}

	public void clickOnCaptureButton() {
		new Actions(driver).pause(Duration.ofSeconds(2)).perform();
		clickOnElement(captureButton);
	}

}
