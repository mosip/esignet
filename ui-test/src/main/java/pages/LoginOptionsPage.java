package pages;

import base.BasePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class LoginOptionsPage extends BasePage {

	public LoginOptionsPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "sign-in-with-esignet")
	WebElement signInWithEsignet;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	public void clickOnSignInWIthEsignet() {
		clickOnElement(signInWithEsignet);
	}

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo);
	}

}
