package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import base.BasePage;
import utils.BaseTestUtil;
import utils.LanguageUtil;

public class MultiLanguagePage extends BasePage {

    public MultiLanguagePage(WebDriver driver) {
        super(driver);
    }

    @FindBy(id = "language_selection")
    WebElement languageSelection;

    public void clickOnLanguageSelection() {
        clickOnElement(languageSelection,"Clicked on language selection option");
    }

    public void clickOnLanguage() {
        String langCode = BaseTestUtil.getThreadLocalLanguage();
        WebElement language = waitForElementVisible(By.xpath("//div[text()='" + LanguageUtil.getDisplayName(langCode) +"']"));
        clickOnElement(language,"Selected the given language");
    }

    public String getLanguageFromCookie() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        return (String) js.executeScript("return window.localStorage.getItem('i18nextLng');");
    }

}