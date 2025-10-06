package pages;

import base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import utils.BaseTestUtil;
import utils.LanguageUtil;

public class MultiLanguagePage extends BasePage {

    public MultiLanguagePage(WebDriver driver) {
        super(driver);
        PageFactory.initElements(driver, this);
    }

    @FindBy(id = "language_selection")
    WebElement languageSelection;

    public void clickOnLanguageSelection() {
        clickOnElement(languageSelection);
    }

    public void clickOnLanguage() {
        String langCode = BaseTestUtil.getThreadLocalLanguage();
        WebElement language = driver.findElement(By.xpath("//div[text()='" + LanguageUtil.getDisplayName(langCode) +"']"));
        clickOnElement(language);
    }

    public String getLanguageFromCookie() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        return (String) js.executeScript("return window.localStorage.getItem('i18nextLng');");
    }

}
