
package stepdefinitions;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.SignUpPage;

public class SignUpStepDef {

	public WebDriver driver;
	BaseTest baseTest;
	SignUpPage signUpPage;

	public SignUpStepDef(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		signUpPage = new SignUpPage(driver);
	}

	@Then("click on signup link")
	public void clickOnSignUp() {
		signUpPage.clickOnSignUp();
	}

}