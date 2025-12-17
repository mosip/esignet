package stepdefinitions;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.ConsentPage;


public class LoginWithInjiStepDefinition {

    private final ConsentPage loginOptionsPage;

    public LoginWithInjiStepDefinition(BaseTest baseTest) {
        WebDriver driver = baseTest.getDriver();
        loginOptionsPage = new ConsentPage(driver);
    }

    @Then("Click on Login with Inji")
    public void clickOnLoginWithInji() {
        loginOptionsPage.clickOnLoginWithInji();
    }
}
