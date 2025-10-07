
package stepdefinitions;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.SignUpPage;

public class SignUpStepDef {

	public WebDriver driver;
	SignUpPage signUpPage;

	public SignUpStepDef(BaseTest baseTest) {
        WebDriver driver = baseTest.getDriver();
        signUpPage = new SignUpPage(driver);
	}

	@Then("click on signup link")
	public void clickOnSignUp() {
		signUpPage.clickOnSignUp();
	}

}