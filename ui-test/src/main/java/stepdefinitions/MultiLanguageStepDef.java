package stepdefinitions;

import org.apache.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.MultiLanguagePage;
import utils.BaseTestUtil;
import utils.LanguageUtil;


public class MultiLanguageStepDef {

    private String languageCookieValue;
    private final MultiLanguagePage multiLanguagePage;
    private static final Logger logger = Logger.getLogger(MultiLanguageStepDef.class);

    public MultiLanguageStepDef(BaseTest baseTest) {
        WebDriver driver = baseTest.getDriver();
        multiLanguagePage = new MultiLanguagePage(driver);
    }

    @When("Click on Language selection option")
    public void clickOnLanguageSelection() {
        multiLanguagePage.clickOnLanguageSelection();
    }

    @When("Select the mandatory language")
    public void selectTheLanguage() {
        multiLanguagePage.clickOnLanguage();
    }

    @When("Get the cookies")
    public void getTheCookies() {
        languageCookieValue = multiLanguagePage.getLanguageFromCookie();
        logger.info("Language value: " + (languageCookieValue != null ? languageCookieValue : "Not found"));
    }

    @Then("Validate the language in cookie")
    public void validateTheLanguageInCookie() {
        Assert.assertNotNull(languageCookieValue, "Language cookie should not be null");
        Assert.assertEquals(languageCookieValue, LanguageUtil.getIsoLanguageCode(BaseTestUtil.getThreadLocalLanguage()), "Language code should be Displayed");
    }

}
