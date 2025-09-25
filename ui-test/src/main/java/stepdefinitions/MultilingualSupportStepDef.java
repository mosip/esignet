package stepdefinitions;

import static org.junit.Assert.assertTrue;
import java.time.Duration;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.HealthServicesPage;
import pages.EsignetIdpPage;
import utils.EsignetConfigManager;

public class MultilingualSupportStepDef {

    public WebDriver driver;
    BaseTest baseTest;
    HealthServicesPage healthServicesPage;
    EsignetIdpPage esignetIdpPage;

    public MultilingualSupportStepDef(BaseTest baseTest) {
        this.baseTest = baseTest;
        this.driver = BaseTest.getDriver();
        healthServicesPage = new HealthServicesPage(driver);
        esignetIdpPage = new EsignetIdpPage(driver);
    }

    @When("Launch the health services url")
    public void launchTheHealthServicesUrl() {
        String url = EsignetConfigManager.getproperty("baseurl");
        driver.get(url);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.titleContains("Health"));
    }

    @Then("User should be redirected to Health service portal UI screen")
    public void userShouldBeRedirectedToHealthServicePortalUIScreen() {
        assertTrue("Health Portal title should be displayed",
                   healthServicesPage.isHealthPortalTitleDisplayed());
    }

    @Then("Verify whether launching url navigates to health service UI Screen")
    public void verifyWhetherLaunchingUrlNavigatesToHealthServiceUIScreen() {
        assertTrue("Health Portal title should be displayed",
                   healthServicesPage.isHealthPortalTitleDisplayed());
    }

    @When("User clicks on sign in with esignet option")
    public void userClicksOnSignInWithEsignetOption() {
        assertTrue("Sign in with esignet button should be displayed",
                   healthServicesPage.isSignInWithEsignetButtonDisplayed());
        healthServicesPage.clickOnSignInWithEsignet();
        
        
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.urlContains("esignet"));
    }

    @Then("User should be redirected to IDP UI screen")
    public void userShouldBeRedirectedToIdpUIScreen() {
        assertTrue("IDP UI screen should be displayed",
                   esignetIdpPage.isIdpUIScreenDisplayed());
    }

    @Then("Verify user is redirected to IDP UI screen")
    public void verifyUserIsRedirectedToIdpUIScreen() {
        assertTrue("Brand logo should be displayed",
                   esignetIdpPage.isBrandLogoDisplayed());
        assertTrue("Login header should be displayed",
                   esignetIdpPage.isLoginHeaderDisplayed());
    }
}
