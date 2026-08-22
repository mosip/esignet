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

    @FindBy(css = "nav button[aria-haspopup='listbox']")
    WebElement languageSelection;

    /** @return false if a real esignet page genuinely isn't reachable (caller should treat as not
     *  applicable and skip); true otherwise. */
    public boolean clickOnLanguageSelection() {
        if (!ensureFreshEsignetLoginPage(By.cssSelector("nav button[aria-haspopup='listbox']"))) {
            return false;
        }
        clickOnElement(languageSelection,"Clicked on language selection option");
        return true;
    }

    public void clickOnLanguage() {
        String langCode = BaseTestUtil.getThreadLocalLanguage();
        By optionLocator = By.xpath("//button[@role='option' and normalize-space()="
                + toXpathLiteral(LanguageUtil.getDisplayName(langCode)) + "]");
        WebElement language;
        try {
            language = waitForElementVisible(optionLocator);
        } catch (org.openqa.selenium.TimeoutException e) {
            clickOnElement(languageSelection, "Re-clicked language selection option (retry)");
            language = waitForElementVisible(optionLocator);
        }
        clickOnElement(language,"Selected the given language");
    }

    public String getLanguageFromCookie() {
        org.openqa.selenium.Cookie cookie = driver.manage().getCookieNamed("thunderid-i18n-language");
        if (cookie == null) {
            cookie = driver.manage().getCookieNamed("i18nextLng");
        }
        return cookie != null ? cookie.getValue() : null;
    }

    public String getNavigatorLanguage() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        return (String) js.executeScript("return navigator.language || navigator.userLanguage;");
    }

    public String getDisplayedLanguageSelection() {
        waitForElementVisible(languageSelection);
        return languageSelection.getText().trim();
    }

}