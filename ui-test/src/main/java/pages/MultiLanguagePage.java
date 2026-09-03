package pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;
import utils.BaseTestUtil;
import utils.LanguageUtil;

public class MultiLanguagePage extends BasePage {

    public MultiLanguagePage(WebDriver driver) {
        super(driver);
    }

    // Thunder: #language_selection; classic: nav button[aria-haspopup='listbox']
    @FindBy(css = "#language_selection, #language_dropdown, nav button[aria-haspopup='listbox']")
    WebElement languageSelection;

    private static final By LANGUAGE_TRIGGER = By.cssSelector(
            "#language_selection, #language_dropdown, nav button[aria-haspopup='listbox']");

    /**
     * @return false if a real esignet page genuinely isn't reachable (caller should treat as not
     *         applicable and skip); true otherwise.
     */
    public boolean clickOnLanguageSelection() {
        if (!ensureFreshEsignetLoginPage(LANGUAGE_TRIGGER) && findLanguageDropdownTrigger() == null) {
            return false;
        }
        WebElement trigger = findLanguageDropdownTrigger();
        if (trigger == null) {
            return false;
        }
        clickOnElement(trigger, "Clicked on language selection option");
        return true;
    }

    public void clickOnLanguage() {
        clickOnLanguage(LanguageUtil.getDisplayName(BaseTestUtil.getThreadLocalLanguage()));
    }

    public void clickOnLanguage(String displayName) {
        WebElement trigger = findLanguageDropdownTrigger();
        if (trigger != null) {
            String current = safeNormalize(trigger.getText());
            if (current.contains(safeNormalize(displayName))) {
                // Re-select the current language so Thunder writes thunderid-i18n-language.
                clickOnElement(trigger, "Opened language dropdown to persist current language");
                WebElement alreadySelected = findLanguageOption(displayName);
                if (alreadySelected != null) {
                    clickOnElement(alreadySelected, "Selected the given language");
                }
                return;
            }
        }
        WebElement language = findLanguageOption(displayName);
        if (language == null && trigger != null) {
            clickOnElement(trigger, "Re-clicked language selection option (retry)");
            language = findLanguageOption(displayName);
        }
        if (language == null) {
            // Authorize URL already has ui_locales; continue when page is already English.
            if ("English".equalsIgnoreCase(displayName)
                    || (driver.getCurrentUrl() != null
                            && driver.getCurrentUrl().toLowerCase().contains("ui_locales=en"))) {
                return;
            }
            throw new TimeoutException("Language option not found: " + displayName);
        }
        clickOnElement(language, "Selected the given language");
    }

    private WebElement findLanguageDropdownTrigger() {
        for (WebElement candidate : driver.findElements(LANGUAGE_TRIGGER)) {
            if (candidate.isDisplayed()) {
                return candidate;
            }
        }
        return null;
    }

    private WebElement findLanguageOption(String displayName) {
        String literal = toXpathLiteral(displayName);
        List<By> locators = List.of(
                By.xpath("//button[@role='option' and normalize-space()=" + literal + "]"),
                By.xpath("//*[@role='menuitem' and normalize-space()=" + literal + "]"),
                By.xpath("//*[contains(@class,'langDropdown') and contains(normalize-space(.),"
                        + literal + ")]"),
                By.xpath("//*[normalize-space()=" + literal
                        + " and (self::button or self::div or @role='option' or @role='menuitem')]"));
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> {
                for (By locator : locators) {
                    for (WebElement candidate : d.findElements(locator)) {
                        if (candidate.isDisplayed()) {
                            return candidate;
                        }
                    }
                }
                return null;
            });
        } catch (TimeoutException e) {
            return null;
        }
    }

    private String safeNormalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    // Verified live: esignet-go persists the chosen language in cookie "thunderid-i18n-language"
    // (2-letter code). Falls back to classic "i18nextLng", then local/session storage.
    public String getLanguageFromCookie() {
        for (String name : java.util.List.of("thunderid-i18n-language", "i18nextLng")) {
            org.openqa.selenium.Cookie cookie = driver.manage().getCookieNamed(name);
            if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Object stored = js.executeScript(
                "var names = ['thunderid-i18n-language', 'i18nextLng'];"
                        + "for (var i = 0; i < names.length; i++) {"
                        + "  var n = names[i];"
                        + "  try { var ls = window.localStorage.getItem(n); if (ls) return ls; } catch (e) {}"
                        + "  try { var ss = window.sessionStorage.getItem(n); if (ss) return ss; } catch (e) {}"
                        + "}"
                        + "try {"
                        + "  var cookies = document.cookie ? document.cookie.split(';') : [];"
                        + "  for (var c = 0; c < cookies.length; c++) {"
                        + "    var part = cookies[c].trim();"
                        + "    for (var i = 0; i < names.length; i++) {"
                        + "      if (part.indexOf(names[i] + '=') === 0) return decodeURIComponent(part.substring(names[i].length + 1));"
                        + "    }"
                        + "  }"
                        + "} catch (e) {}"
                        + "return null;");
        return stored != null ? stored.toString() : null;
    }

    public String getNavigatorLanguage() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        return (String) js.executeScript("return navigator.language || navigator.userLanguage;");
    }

    public String getDisplayedLanguageSelection() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> findLanguageDropdownTrigger() != null);
        } catch (TimeoutException ignored) {
            // Fall through to PageFactory element below.
        }
        WebElement trigger = findLanguageDropdownTrigger();
        if (trigger == null) {
            waitForElementVisible(languageSelection);
            return languageSelection.getText().trim();
        }
        return trigger.getText().trim();
    }
}
