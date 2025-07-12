package stepdefinitions;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.SignUpPage;

public class SignUpStepDef {

    private WebDriver driver;
    private SignUpPage signUpPage;

    public SignUpStepDef(BaseTest baseTest) {
        this.driver = BaseTest.getDriver();
        this.signUpPage = new SignUpPage(driver);
    }

    @Then("click on signup link")
    public void clickOnSignUp() {
        signUpPage.clickOnSignUp();
    }

    @Then("enter phone number")
    public void enterPhoneNumber() {
        signUpPage.enterPhone();
    }

    @Then("click on continue button")
    public void clickOnContinueButton() throws Exception {
        signUpPage.clickOnContinueAndStoreOtp(); // captures OTP in background
    }

    @Then("user enters OTP")
    public void userEntersOtp() {
        signUpPage.enterStoredOtp(); // fills in OTP from stored value
    }

    @Then("click on verify otp button")
    public void clickOnVerifyOtpButton() {
        signUpPage.clickOnVerifyOtp(); // submits the OTP
    }
}
