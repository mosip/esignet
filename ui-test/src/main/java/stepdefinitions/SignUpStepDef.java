
package stepdefinitions;

import static org.junit.Assert.assertTrue;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.SignUpPage;
import utils.EsignetUtil;
import utils.EsignetUtil.RegisteredDetails;

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
	
	@Given("user navigates to sign-up portal URL")
	public void userLaunchesSignupUrl() {
		signUpPage.navigateToSignupPortal();
	}
	
	@Then("user click on Language selection option")
	public void userClickOnLanguageSelection() {
		signUpPage.clickOnLanguageDropdown();
	}
	
	@When("user clicks on Register button")
	public void userClicksOnRegisterButton() {
		signUpPage.clickOnRegisterButton();
	}
	
	@Then("user enters mobile_number in the mobile number text box")
	public void userEnterValidMobileNumber() {
		String mobileNumber = EsignetUtil.generateMobileNumberFromRegex();
		RegisteredDetails.setMobileNumber(mobileNumber);
		signUpPage.enterMobileNumber(mobileNumber);
	}

	@Then("user clicks on the Continue button")
	public void userClickOnContinueButton() {
		signUpPage.clickOnContinueButton();
	}

	@Then("user enters the valid OTP")
	public void userEnterTheOtp() {
		signUpPage.enterOtp("111111");
	}

	@Then("user click on the verify OTP button")
	public void userClickOnVerifyOtpButton() {
		signUpPage.clickOnVerifyOtpButton();
	}

	@Then("user click on Continue button in Success Screen")
	public void clickOnContinueButtonInSucessScreen() {
		signUpPage.clickOnContinueButtonInSucessScreen();
	}

	@When("user enters fullname in the Full Name in Khmer field")
	public void userEntersValidNames() {
		EsignetUtil.FullName names = EsignetUtil.generateNamesFromUiSpec();
		signUpPage.enterFullNameInEnglish(names.english);
		signUpPage.enterFullNameInKhmer(names.khmer);
	}

	private String lastEnteredPassword;

	@Then("user enter the valid password in the Password field")
	public void userEnterValidPassword() {
		lastEnteredPassword = EsignetUtil.generateValidPasswordFromActuator();
		signUpPage.enterPassword(lastEnteredPassword);
	}

	@Then("user enter the valid confirm password in the Confirm Password field")
	public void userEnterValidConfirmPassword() {
		signUpPage.enterConfirmPassword(lastEnteredPassword);
	}
	
	@Then("user click on upload profile photo section")
	public void userClicksOnUploadPhotoSeciton() {
		signUpPage.clickOnUploadPhoto();
	}
	
	@Then("user click on capture button")
	public void userClickOnCaptureButton() {
		signUpPage.clickOnCaptureButton();
	}

	@Then("user accepts the Terms and Condition")
	public void verifyContinueButtonEnabledWhenAllMandatoryFieldsFilled() {
		signUpPage.checkTermsAndConditions();
	}

	@Then("user clicks on Continue button in Setup Account Page")
	public void userClicksOnContinueButtonInSetpuAccountPage() {
		signUpPage.clickOnSetupAccountContinueButton();
	}
	
	@Then("verify success screen is displayed")
	public void verifyThenSuccessMessageDisplayed() {
		assertTrue(signUpPage.isAccountCreatedSuccessMessageDisplayed());
	}
}