package stepdefinitions;

import org.apache.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.LoginOptionsPage;
import pages.MultiLanguagePage;
import utils.BaseTestUtil;
import utils.LanguageUtil;
import utils.ResourceBundleLoader;


public class MultiLanguageStepDef {

    private String languageCookieValue;
    private final WebDriver driver;
    private final MultiLanguagePage multiLanguagePage;
    private final LoginOptionsPage loginOptionsPage;
    private static final Logger logger = Logger.getLogger(MultiLanguageStepDef.class);

    public MultiLanguageStepDef(BaseTest baseTest) {
        driver = BaseTest.getDriver();
        multiLanguagePage = new MultiLanguagePage(driver);
        loginOptionsPage = new LoginOptionsPage(driver);
    }

    @When("click on Language selection option")
    public void clickOnLanguageSelection() {
        multiLanguagePage.clickOnLanguageSelection();
    }

    @When("select the mandatory language")
    public void selectTheLanguage() {
        multiLanguagePage.clickOnLanguage();
    }

    @When("get the cookies")
    public void getTheCookies() {
        languageCookieValue = multiLanguagePage.getLanguageFromCookie();
        logger.info("Language value: " + (languageCookieValue != null ? languageCookieValue : "Not found"));
    }

    @Then("validate the language in cookie")
    public void validateTheLanguageInCookie() {
        Assert.assertNotNull(languageCookieValue, "Language cookie should not be null");
        Assert.assertEquals(languageCookieValue, LanguageUtil.getIsoLanguageCode(BaseTestUtil.getThreadLocalLanguage()), "Language code should be Displayed");
    }

    @Then("verify IDP UI uses default language configured in env-config")
    public void verifyIdpUiUsesDefaultLanguageConfiguredInEnvConfig() {
        String defaultLang = LanguageUtil.fetchDefaultLangFromEnvConfig();
        String expectedIsoCode = LanguageUtil.resolveDefaultLangToIsoCode(defaultLang);
        String expectedDisplayName = LanguageUtil.getDisplayNameFromIso(expectedIsoCode);

        String currentUrl = driver.getCurrentUrl();
        Assert.assertFalse(currentUrl.contains("ui_locales="),
                "Authorize URL should not contain ui_locales parameter (MOSIP-24002 TC_14)");

        String navigatorLanguage = multiLanguagePage.getNavigatorLanguage();
        logger.info("Navigator language: " + navigatorLanguage);
        Assert.assertFalse(LanguageUtil.isSupportedBrowserLocale(navigatorLanguage),
                "System locale should not match a supported language so DEFAULT_LANG fallback is used. navigator.language: "
                        + navigatorLanguage);

        String storedLanguage = multiLanguagePage.getLanguageFromCookie();
        logger.info("Stored language (i18nextLng): " + storedLanguage);
        Assert.assertNotNull(storedLanguage, "Language preference should be stored after page load");
        Assert.assertTrue(LanguageUtil.matchesLanguageCode(storedLanguage, expectedIsoCode),
                "Stored language should match DEFAULT_LANG from env-config.js. Expected: " + expectedIsoCode
                        + ", actual: " + storedLanguage);

        String displayedLanguage = multiLanguagePage.getDisplayedLanguageSelection();
        logger.info("Displayed language selection: " + displayedLanguage);
        Assert.assertEquals(displayedLanguage, expectedDisplayName,
                "Language dropdown should reflect DEFAULT_LANG from env-config.js");

        String loginWithIdTemplate = ResourceBundleLoader.getByIsoCode(expectedIsoCode, "signInOption.login_with_id");
        String otpOption = ResourceBundleLoader.getByIsoCode(expectedIsoCode, "signInOption.OTP");
        String expectedOtpText = loginWithIdTemplate.replace("{{option}}", otpOption);
        String actualOtpText = loginOptionsPage.getLoginWithOtpButtonText();
        Assert.assertEquals(actualOtpText, expectedOtpText,
                "Login options UI should be displayed in DEFAULT_LANG from env-config.js");
    }

}
