package pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import base.BasePage;
import utils.EsignetConfigManager;

public class SignUpPage extends BasePage {

	public SignUpPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "signup-url-button")
	WebElement signUp;
	
	@FindBy(xpath = "//h1[@class='text-center text-2xl']")
	WebElement signUpErrorHearder;
	
	@FindBy(xpath = "//p[@class='text-center text-gray-500']")
	WebElement signUpErrorMessage;
	
	@FindBy(id = "reset-password-button")
	WebElement resetPasswordBtn;
	
	@FindBy(id = "register-button")
	WebElement registerButton;

	public void clickOnSignUp() {
		clickOnElement(signUp);
	}
	
	public void navigateToSignupPortal() {
	    driver.get(EsignetConfigManager.getSignupPortalUrl());
	}
	
	public boolean isHeaderInSignUpErrorScreenDisplayed() {
		return isElementVisible(signUpErrorHearder);
	}
	
	public boolean isMessageInSignUpErrorScreenDisplayed() {
		return isElementVisible(signUpErrorMessage);
	}
	
	public boolean isResetPasswordButtonDisplayed() {
		return isElementVisible(resetPasswordBtn);
	}
	
	public boolean isRegisterButtonDisplayed() {
		return isElementVisible(registerButton);
	}
	
	public void clickOnRegisterButton() {
		clickOnElement(registerButton);
	}
	
}
