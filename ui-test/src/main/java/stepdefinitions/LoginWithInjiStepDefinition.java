package stepdefinitions;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.LoginOptionsPage;


public class LoginWithInjiStepDefinition {

    private final LoginOptionsPage loginOptionsPage;

    public LoginWithInjiStepDefinition(BaseTest baseTest) {
        WebDriver driver = baseTest.getDriver();
        loginOptionsPage = new LoginOptionsPage(driver);
    }

    @Then("Click on Login with Inji")
    public void clickOnLoginWithInji() {
        loginOptionsPage.clickOnLoginWithInji();
    }
}
