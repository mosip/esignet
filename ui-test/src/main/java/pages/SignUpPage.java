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
		clickOnElement(signUp,"Clicked on signup button");
	}

	public void navigateToSignupPortal() {
		driver.get(EsignetConfigManager.getSignupUrl());
	}

	public void clickOnRegisterButton() {
		clickOnElement(registerButton,"Clicked on register button");
	}

	public void enterMobileNumber(String number) {
		enterText(enterMobileNumberField, number,"Entered the mobile number");
	}

	public void clickOnContinueButton() {
		clickOnElement(continueButton,"Clicked on continue button");
	}

	public void enterOtp(String otp) {
		if (otp.length() > otpInputFields.size()) {
			throw new IllegalArgumentException("OTP length exceeds available input fields");
		}
		for (int i = 0; i < otp.length(); i++) {
			WebElement field = otpInputFields.get(i);
			field.click();
			field.sendKeys(String.valueOf(otp.charAt(i)));
		}
	}

	public void clickOnVerifyOtpButton() {
		clickOnElement(verifyOtpButton,"Clicked on verify otp button");
	}

	public void clickOnContinueButtonInSucessScreen() {
		clickOnElement(continueButtonInSuccessPage,"Clicked on continue button in success screen");
	}

	public void clickOnUploadPhoto() {
		clickOnElement(uploadPhoto,"Clicked on upload photo section");
	}

	public void clickOnCaptureButton() {
		new Actions(driver).pause(Duration.ofSeconds(1)).perform();
		clickOnElement(captureButton,"Clicked on Capture button");
	}

	public void clickOnSetupAccountContinueButton() {
		clickOnElement(setupContinueButton,"Clicked on continue button in account setup screen");
	}

	public boolean isAccountCreatedSuccessfullyMessageDisplayed() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(50));
		wait.until(ExpectedConditions.visibilityOf(accountCreatedSuccessfullyMessage));
		return isElementVisible(accountCreatedSuccessfullyMessage,"Verified account created successfully message displayed");
	}

}