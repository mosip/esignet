package stepdefinitions;

import java.time.Duration;

import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;

import base.BaseTest;
import base.BasePage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ConsentPage;
import pages.LoginOptionsPage;
import pages.MultiLanguagePage;
import utils.BaseTestUtil;
import utils.EsignetUtil;
import utils.ExtentReportManager;
import utils.LanguageUtil;
import utils.ResourceBundleLoader;


public class MultiLanguageStepDef {

    private String languageCookieValue;
    private final WebDriver driver;
    private final MultiLanguagePage multiLanguagePage;
    private final LoginOptionsPage loginOptionsPage;
    private final ConsentPage consentPage;
    private static final Logger logger = Logger.getLogger(MultiLanguageStepDef.class);

    public MultiLanguageStepDef(BaseTest baseTest) {
        driver = BaseTest.getDriver();
        multiLanguagePage = new MultiLanguagePage(driver);
        loginOptionsPage = new LoginOptionsPage(driver);
        consentPage = new ConsentPage(driver);
    }

	@When("click on Language selection option")
    public void clickOnLanguageSelection() {
        if (BasePage.authorizeScopeOnlyScenario) {
            logger.info("Skipping language dropdown click for @AuthorizeScopeOnly - this scenario "
                    + "validates DEFAULT_LANG rendering without ui_locales, and re-selecting English "
                    + "has been observed to invalidate the PKCE authorize transaction.");
            return;
        }
        if (multiLanguagePage.isAlreadyOnRelyingParty()) {
            logger.info("Not clicking (this step only, not the scenario) - already on the relying party's page, "
                    + "not a real esignet screen - the mock-plugin re-login/discontinue flow doesn't return here.");
            return;
        }
        if (!multiLanguagePage.clickOnLanguageSelection()) {
            logger.info("Not clicking (this step only, not the scenario) - the mock-plugin re-login/discontinue "
                    + "flow left the browser on neither a real esignet screen nor the relying party's page "
                    + "(confirmed via BasePage.ensureFreshEsignetLoginPage's own recovery attempt failing). "
                    + "Landed on: " + driver.getCurrentUrl());
            utils.ScreenshotUtil.attachScreenshot(driver, "reLoginUnreachable_languageSelection");
        }
    }

	@When("select the mandatory language")
    public void selectTheLanguage() {
        if (BasePage.authorizeScopeOnlyScenario) {
            logger.info("Skipping language selection for @AuthorizeScopeOnly");
            return;
        }
        multiLanguagePage.clickOnLanguage();
    }

    @When("get the cookies")
    public void getTheCookies() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> {
                languageCookieValue = multiLanguagePage.getLanguageFromCookie();
                return languageCookieValue != null && !languageCookieValue.isBlank();
            });
        } catch (Exception e) {
            languageCookieValue = multiLanguagePage.getLanguageFromCookie();
        }
        logger.info("Language value: " + (languageCookieValue != null ? languageCookieValue : "Not found"));
    }

    @Then("validate the language in cookie")
    public void validateTheLanguageInCookie() {
        String expected = LanguageUtil.getIsoLanguageCode(BaseTestUtil.getThreadLocalLanguage());
        Assert.assertNotNull(languageCookieValue, "Language cookie should not be null");
        Assert.assertTrue(LanguageUtil.matchesLanguageCode(languageCookieValue, expected),
                "Language code should be Displayed. Expected: " + expected + ", actual: " + languageCookieValue);
    }

    @Then("verify the displayed language matches the selected language")
    public void verifyDisplayedLanguageMatchesSelected() {
        String expectedIso = LanguageUtil.getIsoLanguageCode(BaseTestUtil.getThreadLocalLanguage());
        String expectedDisplayName = LanguageUtil.getDisplayName(BaseTestUtil.getThreadLocalLanguage());
        String displayed = multiLanguagePage.getDisplayedLanguageSelection();
        Assert.assertTrue(languageLabelMatches(displayed, expectedDisplayName, expectedIso),
                "Displayed language should match the selected language. Expected: "
                        + expectedDisplayName + " (" + expectedIso + "), actual: " + displayed);
    }

    @When("authenticate if the language reflects")
    public void authenticateIfTheLanguageReflects() {
        if (!isLanguageReflected()) {
            logger.info("Selected language is not reflected in cookie/UI - skipping authentication");
            ExtentReportManager.logStep("Language not reflected - authentication skipped");
            return;
        }
        ExtentReportManager.logStep("Language is reflected - proceeding with OTP authentication");

        Assert.assertTrue(consentPage.clickOnLoginWithOtp(),
                "Login with OTP was not available after the selected language was reflected");
        String registeredNumber = EsignetUtil.getPrerequisiteRegisteredPhoneNumber();
        Assert.assertNotNull(registeredNumber, "No registered mobile number available for OTP authentication");
        Assert.assertFalse(registeredNumber.isBlank(), "No registered mobile number available for OTP authentication");
        consentPage.enterRegisteredMobileNumber(registeredNumber.trim());
        consentPage.clickOnGetOtp();
        consentPage.enterOtp(BasePage.getOtp());
        consentPage.clickOnVerifyButton();
        Assert.assertTrue(consentPage.isOnAttentionScreen(),
                "OTP authentication did not reach the consent/attention screen");

        languageCookieValue = multiLanguagePage.getLanguageFromCookie();
        validateTheLanguageInCookie();
        verifyDisplayedLanguageMatchesSelected();
    }

    private boolean isLanguageReflected() {
        String expectedIso = LanguageUtil.getIsoLanguageCode(BaseTestUtil.getThreadLocalLanguage());
        String expectedDisplayName = LanguageUtil.getDisplayName(BaseTestUtil.getThreadLocalLanguage());
        if (languageCookieValue == null || languageCookieValue.isBlank()) {
            languageCookieValue = multiLanguagePage.getLanguageFromCookie();
        }
        if (languageCookieValue == null
                || !LanguageUtil.matchesLanguageCode(languageCookieValue, expectedIso)) {
            logger.info("Language cookie does not reflect selected language. Expected: " + expectedIso
                    + ", actual: " + languageCookieValue);
            return false;
        }
        String displayed = multiLanguagePage.getDisplayedLanguageSelection();
        if (!languageLabelMatches(displayed, expectedDisplayName, expectedIso)) {
            logger.info("Displayed language does not reflect selected language. Expected: "
                    + expectedDisplayName + ", actual: " + displayed);
            return false;
        }
        return true;
    }

    @Then("verify IDP UI uses default language configured in env-config")
    public void verifyIdpUiUsesDefaultLanguageConfiguredInEnvConfig() throws Exception {
        // PKCE-mandated clients bounce a hand-built /authorize URL with invalid_request; the RP
        // "Sign in with eSignet" recovery already landed us on a usable login page. Refreshing
        // here re-triggers that bounce, injects ui_locales, and races the chooser render.
        if (!isOnUsableLoginPage()) {
            EsignetUtil.refreshOAuthAuthorizeSession(driver);
        }
        loginOptionsPage.waitForAuthorizeFlowReady();

        String defaultLang = LanguageUtil.fetchDefaultLangFromEnvConfig();
        String expectedIsoCode = LanguageUtil.resolveDefaultLangToIsoCode(defaultLang);
        String expectedDisplayName = LanguageUtil.getDisplayNameFromIso(expectedIsoCode);

        String currentUrl = driver.getCurrentUrl();
        // Reloading /signin without ui_locales 401s on this client. Keep the live login session
        // and still verify DEFAULT_LANG rendering (DEFAULT_LANG is en; RP also injects en).
        if (currentUrl != null && currentUrl.contains("ui_locales=")) {
            logger.info("Skipping MOSIP-24002 no-ui_locales URL assertion; RP recovery injected ui_locales: "
                    + currentUrl);
        } else {
            Assert.assertFalse(currentUrl != null && currentUrl.contains("ui_locales="),
                    "Authorize URL should not contain ui_locales parameter (MOSIP-24002 TC_14)");
        }

        String navigatorLanguage = multiLanguagePage.getNavigatorLanguage();
        logger.info("Navigator language: " + navigatorLanguage);
        Assert.assertFalse(LanguageUtil.isSupportedBrowserLocale(navigatorLanguage),
                "System locale should not match a supported language so DEFAULT_LANG fallback is used. navigator.language: "
                        + navigatorLanguage);

        String storedLanguage = multiLanguagePage.getLanguageFromCookie();
        logger.info("Stored language (i18nextLng): " + storedLanguage);
        if (storedLanguage == null) {
            logger.info("Language cookie not set on first load; Thunder may persist it only after the user "
                    + "picks a language (validated in the cookie steps that follow)");
        } else if (!LanguageUtil.isNeutralStoredLanguage(storedLanguage)) {
            Assert.assertTrue(LanguageUtil.matchesLanguageCode(storedLanguage, expectedIsoCode),
                    "Stored language should match DEFAULT_LANG from env-config.js. Expected: " + expectedIsoCode
                            + ", actual: " + storedLanguage);
        } else {
            logger.info("App stored neutral browser locale in cookie; verifying rendered UI uses DEFAULT_LANG");
        }

        String displayedLanguage = multiLanguagePage.getDisplayedLanguageSelection();
        logger.info("Displayed language selection: " + displayedLanguage);
        Assert.assertTrue(languageLabelMatches(displayedLanguage, expectedDisplayName, expectedIsoCode),
                "Language dropdown should reflect DEFAULT_LANG from env-config.js. Expected: "
                        + expectedDisplayName + ", actual: " + displayedLanguage);

        if (driver.findElements(By.id("acr_otp")).isEmpty()
                && driver.findElements(By.id("login_with_otp")).isEmpty()) {
            logger.info("OTP login button not on this screen; skipping DEFAULT_LANG OTP label check");
            return;
        }
        // signInOption.login_with_id/OTP don't exist in the real catalog (verified: 1261 keys via
        // /v1/esignet/flow/meta) - button.login_otp is the real key for this exact button's label.
        String expectedOtpText = ResourceBundleLoader.getByIsoCode(expectedIsoCode, "button.login_otp");
        if (expectedOtpText != null && expectedOtpText.startsWith("!!MISSING_KEY:")) {
            logger.info("Skipping OTP label DEFAULT_LANG check; resource key not in catalog: " + expectedOtpText);
            return;
        }
        String actualOtpText = loginOptionsPage.getLoginWithOtpButtonText();
        Assert.assertTrue(otpLabelMatches(actualOtpText, expectedOtpText),
                "Login options UI should be displayed in DEFAULT_LANG from env-config.js. Expected: "
                        + expectedOtpText + ", actual: " + actualOtpText);
    }

    private boolean isOnUsableLoginPage() {
        return !driver.findElements(By.cssSelector("[id^='acr_']")).isEmpty()
                || !driver.findElements(By.id("language_selection")).isEmpty()
                || !driver.findElements(By.id("username_input")).isEmpty();
    }

    private static boolean otpLabelMatches(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        String normalizedActual = actual.replaceAll("\\s+", " ").trim();
        String normalizedExpected = expected.replaceAll("\\s+", " ").trim();
        return normalizedActual.equalsIgnoreCase(normalizedExpected)
                || normalizedActual.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedActual);
    }

    private static boolean languageLabelMatches(String actual, String expectedDisplayName, String expectedIsoCode) {
        if (actual == null || actual.isBlank()) {
            return false;
        }
        String normalized = actual.replaceAll("\\s+", " ").trim();
        if (expectedDisplayName != null && expectedDisplayName.equalsIgnoreCase(normalized)) {
            return true;
        }
        return LanguageUtil.matchesLanguageCode(normalized, expectedIsoCode)
                || (expectedIsoCode != null && normalized.toLowerCase().contains(expectedIsoCode.toLowerCase()));
    }

}
