package stepdefinitions;

import static org.junit.Assert.assertTrue;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import pages.LoginOptionsPage;

public class LoginOptionsStepDefinition {

	public WebDriver driver;
	LoginOptionsPage loginOptionsPage;

	public LoginOptionsStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);

	}

	@Given("Click on Sign In with eSignet")
	public void clickOnSignInWithEsignet() {
		loginOptionsPage.clickOnSignInWIthEsignet();
	}

	@Then("Validate that the logo is displayed")
	public void validateTheLogo() {
		assertTrue(loginOptionsPage.isLogoDisplayed());
	}
}
