package pages;

import base.BasePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class HealthServicesPage extends BasePage {

    public HealthServicesPage(WebDriver driver) {
        super(driver);
        PageFactory.initElements(driver, this);
    }

    @FindBy(xpath = "//span[@class='title-font text-3xl text-gray-900 font-medium']")
    WebElement healthPortalTitle;

    @FindBy(id = "sign-in-with-esignet")
    WebElement signInWithEsignetButton;

    public boolean isHealthPortalTitleDisplayed() {
        return isElementVisible(healthPortalTitle);
    }

    public String getHealthPortalTitleText() {
        return getText(healthPortalTitle);
    }

    public boolean isSignInWithEsignetButtonDisplayed() {
        return isElementVisible(signInWithEsignetButton);
    }

    public void clickOnSignInWithEsignet() {
        clickOnElement(signInWithEsignetButton);
    }
}
