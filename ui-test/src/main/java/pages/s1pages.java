package pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import base.BasePage;

public class s1pages extends BasePage {

	public s1pages(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}
	
	@FindBy(xpath = "//span[@class='title-font text-3xl text-gray-900 font-medium']")
	WebElement userIsOnRelyingPortal;
	
	@FindBy(xpath = "//span[@class='SignInWithEsignet-module_textbox__k2CkO']")
	WebElement signInEsignetButton;
	
	@FindBy(id = "signup-url-button")
    WebElement signUpWithUnifiedLoginButton;
	
	public boolean isUserOnRelyingPortal() {
	    return userIsOnRelyingPortal.isDisplayed();
	}
	
	public void clickOnSignInEsignetButton() {
	    signInEsignetButton.click();
	}
	
	public boolean isSignUpWithUnifiedLoginButtonDisplayed() {
        return signUpWithUnifiedLoginButton.isDisplayed();
    }
	
	public void clickOnSignUpWithUnifiedLoginButton() {
        signUpWithUnifiedLoginButton.click();
    }
	
	
	
	
}
