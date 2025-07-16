package stepdefinitions;

import static org.junit.Assert.assertTrue;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import pages.LoginOptionsPage;

public class LoginOptionsStepDefinition {
	
	public WebDriver driver;
	BaseTest baseTest;
	LoginOptionsPage loginOptionsPage;
	
	public LoginOptionsStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
	}
	
	@Given("click on Sign In with eSignet")
	public void clickOnSignInWithEsignet() {
		loginOptionsPage.clickOnSignInWIthEsignet();
	}
	
	@Then("validate that the logo is displayed")
	public void validateTheLogo() {
		assertTrue(loginOptionsPage.isLogoDisplayed());
	}
}
