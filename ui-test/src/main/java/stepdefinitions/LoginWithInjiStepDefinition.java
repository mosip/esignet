package stepdefinitions;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.LoginOptionsPage;
import utils.ExtentReportManager;

public class LoginWithInjiStepDefinition {

	private final LoginOptionsPage loginOptionsPage;
	private boolean injiApplicable = true;

	public LoginWithInjiStepDefinition(BaseTest baseTest) {
		WebDriver driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
	}

	@Then("Click on Login with Inji")
	public void clickOnLoginWithInji() {
		if (!loginOptionsPage.isLoginWithInjiDisplayed()) {
			injiApplicable = false;
			String reason = "Login with Inji is not offered by this environment's default client/policy - "
					+ "verified live (only OTP/Password/Biometrics render).";
			ExtentReportManager.notApplicable(reason);
			return;
		}
		loginOptionsPage.clickOnLoginWithInji();
	}

	@Then("validate the logo alignment")
	public void validateLogoAlignment() {
		if (!injiApplicable) {
			ExtentReportManager.notApplicable("Login with Inji screen never opened - nothing to validate.");
			return;
		}
		Assert.assertTrue(loginOptionsPage.isLogoDisplayed(), "eSignet logo is not displayed");
	}
}
