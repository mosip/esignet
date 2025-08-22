package pages;

import base.BasePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class EsignetIdpPage extends BasePage {

    public EsignetIdpPage(WebDriver driver) {
        super(driver);
        PageFactory.initElements(driver, this);
    }

    @FindBy(xpath = "//img[@class='brand-logo']")
    WebElement brandLogo;

    @FindBy(id = "login-header")
    WebElement loginHeader;

    public boolean isBrandLogoDisplayed() {
        return isElementVisible(brandLogo);
    }

    public boolean isLoginHeaderDisplayed() {
        return isElementVisible(loginHeader);
    }

    public boolean isIdpUIScreenDisplayed() {
        return isBrandLogoDisplayed() && isLoginHeaderDisplayed();
    }
}
