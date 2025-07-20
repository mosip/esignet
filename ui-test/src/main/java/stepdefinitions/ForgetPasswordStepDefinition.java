package stepdefinitions;

import java.util.List;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import io.cucumber.java.en.When;
import io.cucumber.datatable.DataTable;
import java.util.List;
import java.util.Map;
import base.BaseTest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ForgetPasswordPage;
import pages.LoginOptionsPage;

public class ForgetPasswordStepDefinition {

	public WebDriver driver;
	BaseTest baseTest;
	LoginOptionsPage loginOptionsPage;
	ForgetPasswordPage forgetPasswordPage;

	public ForgetPasswordStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = BaseTest.getDriver();
		this.forgetPasswordPage = new ForgetPasswordPage(driver);
	}

	@Then("user click on Login with password")
	public void user_click_on_login_with_password() {
		forgetPasswordPage.clickOnLoginWithPassword();
	}

	@Then("user verify forget password link")
	public void user_verify_forget_password_link() {
		Assert.assertTrue(forgetPasswordPage.isforgetPasswordLinkDisplayed(),
				"Forget Password link should be visible on the page.");
	}

	@Then("user click on forget password link")
	public void user_click_on_forget_password_link() {
		forgetPasswordPage.clickOnForgetPasswordLink();
	}

	@Then("user verify browser redirected to reset-password")
	public void user_verify_browser_redirected_to_reset_password() {
		Assert.assertTrue(forgetPasswordPage.isRedirectedToResetPasswordPage(), "Redirected to reset-password link");
	}

	@Then("user verify country code prefix")
	public void user_verify_country_code_prefix() {
		Assert.assertTrue(forgetPasswordPage.isphonePrefixDisplayed(), "Country code prefix displayed");
	}

	@Then("user verify the water mark text inside phonenumber")
	public void user_verify_the_water_mark_text_inside_phonenumber() {
		Assert.assertTrue(forgetPasswordPage.isWaterMarkDisplayed(), "Water mark displayed inside phonenumber");
	}

	@Then("user verify country code is not editable")
	public void user_verify_country_code_is_not_editable() {
		Assert.assertTrue(forgetPasswordPage.isCountryCodeNonEditable(), "Country code is not editable");
	}

	@When("user enters {string} into the mobile number field")
	public void user_enters_mobile_number(String number) {
		forgetPasswordPage.enterPhoneNumber(number);
	}

	@When("user clicks outside the input to trigger validation")
	public void user_clicks_outside_to_trigger_validation() {
		forgetPasswordPage.triggerPhoneValidation();
	}

	@Then("phone number should be {string}")
	public void phone_number_should_be(String validity) {
		boolean isErrorVisible;

		try {
			isErrorVisible = forgetPasswordPage.isPhoneErrorVisible();
		} catch (Exception e) {
			isErrorVisible = false;
		}
		if (validity.equalsIgnoreCase("valid")) {
			Assert.assertFalse(isErrorVisible, "Expected no error for valid phone number, but error is shown.");
		} else {
			Assert.assertTrue(isErrorVisible, "Expected error for invalid phone number, but none is shown.");
		}
	}

	@Then("user verify forget password heading")
	public void user_verify_forget_password_headning() {
		Assert.assertTrue(forgetPasswordPage.isForgetPasswordHeadningVisible(), "Forget password heading visible");
	}

	@Then("user verify back button on forget password")
	public void user_verify_back_button_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isBackButtonOnForgePasswordVisible(),
				"Back button on foget password visible");
	}

	@Then("user verify subheading on forget password")
	public void user_verify_subeading_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isForgetPasswordSubHeadningVisible(),
				"Subheading on foget password visible");
	}

	@Then("user verify username label on forget password")
	public void user_verify_user_label_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isUserNameLabelVisible(), "User Label on foget password visible");
	}

	@Then("user verify fullname label on forget password")
	public void user_verify_fullname_label_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isFullNameLabelVisible(), "Fullname Label on foget password visible");
	}

	@Then("user verify continue button on forget password")
	public void user_verify_lang_selection_button_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isLangSelectionButtonVisible(),
				"Lang selection button on foget password visible");
	}

	@Then("user verify footer on forget password")
	public void user_verify_footer_on_forget_password() {
		Assert.assertTrue(forgetPasswordPage.isFooterPoweredByVisible(),
				"Lang selection button on foget password visible");
	}

	@Then("mobile number input should remain empty")
	public void mobile_number_input_should_remain_empty() {
		Assert.assertEquals(forgetPasswordPage.getEnteredPhoneNumber(), "",
				"Field should remain empty for invalid input like alphanumeric or special characters.");
	}

	@Then("user verify continue button is not enabled")
	public void user_verify_continue_button_is_not_enabled() {
		Assert.assertTrue(forgetPasswordPage.isContinueButtonDisabled(), "Continue button disabled");
	}

	@When("user enters {string} into the fullname field")
	public void user_enters_fullname(String input) {
		forgetPasswordPage.enterFullName(input);
	}

	@Then("system should show error for fullname {string}")
	public void system_should_show_error_for_fullname(String expectError) {
		Assert.assertEquals(forgetPasswordPage.isFullNameErrorVisible(), Boolean.parseBoolean(expectError),
				"Mismatch in fullname error visibility");
	}

	@Then("user verify full name error message")
	public void fullname_error_displayed() {
	  Assert.assertTrue(forgetPasswordPage.isFullNameErrorVisible());
	}

	
	@Then("user verify full name error message not displayed")
	public void fullname_error_not_displayed() {
	    Assert.assertFalse(forgetPasswordPage.isFullNameErrorPresent(),
	        "Expected no error message, but one was present");
	}


	@Then("only 30 characters are retained in the fullname field")
	public void only_30_chars_retained() {
	  String actual = forgetPasswordPage.getEnteredFullName();
	  Assert.assertEquals(actual.length(), 30);
	}
	
	@Then("user verify continue button is enabled")
	public void user_verify_continue_button_is_enabled() {
		Assert.assertTrue(forgetPasswordPage.isContinueButtonEnabled(), "Continue button enabled");
	}

}
