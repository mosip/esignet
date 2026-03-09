package pages;

import java.time.Duration;
import java.util.List;

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

	@FindBy(id = "register-button")
	WebElement registerButton;

	@FindBy(id = "phone_input")
	WebElement enterMobileNumberField;

	@FindBy(id = "continue-button")
	WebElement continueButton;

	@FindBy(xpath = "//div[@class='pincode-input-container']/input")
	List<WebElement> otpInputFields;

	@FindBy(id = "verify-otp-button")
	WebElement verifyOtpButton;

	@FindBy(id = "mobile-number-verified-continue-button")
	WebElement continueButtonInSuccessPage;

	@FindBy(xpath = "//div[@class='alternate-icon-div']")
	WebElement uploadPhoto;

	@FindBy(xpath = "//button[contains(@id,'capture-button')]")
	WebElement captureButton;

	@FindBy(id = "form-submit-button")
	WebElement setupContinueButton;

	@FindBy(xpath = "//div[@class='text-center text-lg font-semibold']")
	WebElement accountCreatedSuccessfullyMessage;

	public void clickOnSignUp() {
		clickOnElement(signUp);
	}

	public void navigateToSignupPortal() {
		driver.get(EsignetConfigManager.getSignupUrl());
	}

	public void clickOnRegisterButton() {
		clickOnElement(registerButton);
	}

	public void enterMobileNumber(String number) {
		enterText(enterMobileNumberField, number);
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

	public void clickOnUploadPhoto() {
		clickOnElement(uploadPhoto);
	}

	public void clickOnCaptureButton() {
		new Actions(driver).pause(Duration.ofSeconds(1)).perform();
		clickOnElement(captureButton);
	}

	public void clickOnSetupAccountContinueButton() {
		clickOnElement(setupContinueButton);
	}

	public boolean isAccountCreatedSuccessfullyMessageDisplayed() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(50));
		wait.until(ExpectedConditions.visibilityOf(accountCreatedSuccessfullyMessage));
		return isElementVisible(accountCreatedSuccessfullyMessage);
	}

}
