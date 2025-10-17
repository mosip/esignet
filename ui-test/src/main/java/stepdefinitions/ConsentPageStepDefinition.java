package stepdefinitions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;

import org.junit.Assert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ConsentPage;
import pages.SmtpPage;

public class ConsentPageStepDefinition {

	public WebDriver driver;
	ConsentPage consentPage;
	SmtpPage smtpPage;
	BaseTest baseTest;

	public ConsentPageStepDefinition(BaseTest baseTest) {
		this.baseTest = baseTest;
		this.driver = baseTest.getDriver();
		consentPage = new ConsentPage(driver);
		smtpPage = new SmtpPage(driver);
	}

	private String authorizeUrl;

	@Given("click on Sign In with eSignet")
	public void clickOnSignInWithEsignet() throws Exception {
		consentPage.clickOnSignInWIthEsignet();
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.urlContains("#"));
		String currentUrl = driver.getCurrentUrl();
		consentPage.setAuthorizeUrl(currentUrl);
	}

	@Then("validate that the logo is displayed")
	public void validateTheLogo() {
		assertTrue(consentPage.isLogoDisplayed());
	}

	@Then("user click on Login with Otp")
	public void clickOnLoginWithOtp() {
		consentPage.clickOnLoginWithOtp();
	}

	@Then("user selects UIN or VID option")
	public void userSelectsUinOrVid() {
		consentPage.clickOnUinOrVidOption();
	}

	@When("user enter the single_char UIN in the UINorVID text field")
	public void userEnterSingleCharUin() {
		consentPage.enterUinOrVid("2");
	}

	@Then("verify get otp button is enabled")
	public void verifyOtpBtnIsEnabled() {
		assertTrue(consentPage.isGetOtpButtonEnabled());
	}

	@Then("user click on get otp button")
	public void userClickOnGetOtpBtn() {
		consentPage.clickOnGetOtp();
	}

	@Then("verify user is redirected to Login screen of eSignet")
	public void verifyUserIsOnLoginPage() {
		assertTrue(consentPage.isLoginScreenDisplayed());
	}

	@Then("verify Please Enter Valid Individual ID error is displayed")
	public void verifyErrorDisplayed() {
		assertTrue(consentPage.isInvalidIdErrorDisplayed());
	}

	@When("user enter the invalid UIN in the UINorVID text field")
	public void userEnterInvalidUin() {
		consentPage.enterUinOrVid("5061434369");
	}

	@When("user enter the valid UIN in the UINorVID text field")
	public void userEnterUin() {
		String uin = baseTest.getUin();
		consentPage.enterUinOrVid(uin);
	}

	@Then("verify user is navigated to Otp page")
	public void userNavigatesToOtpScreen() {
		assertTrue(consentPage.isNavigatedToOtpPage());
	}

	@Then("validate VerifyOtp button is disabled")
	public void validateVerifyOtpBtnIsDisabled() {
		assertFalse(consentPage.isVerifyOtpButtonEnabled());
	}

	@Then("verify OTP timer starts and keeps decreasing")
	public void verifyOtpTimerDecreasing() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		int initialValue = consentPage.getOtpTimerValue();
		wait.until(driver -> consentPage.getOtpTimerValue() < initialValue);
		int laterValue = consentPage.getOtpTimerValue();
		assertTrue(laterValue < initialValue);
	}

	@Then("user enters the {string}")
	public void userEnterOtp(String otp) {
		consentPage.enterOtp(otp);
	}

	@Then("click on verify Otp button")
	public void userClickOnVerifyOtpBtn() {
		consentPage.clickOnVerifyButton();
	}

	@Then("validate VerifyOtp button is enabled")
	public void validateVerifyOtpBtnIsEnabled() {
		assertTrue(consentPage.isVerifyOtpButtonEnabled());
	}

	@Then("verify OTP authentication failed.Please try again. error is displayed")
	public void verifyInvalidOtpErrorDisplayed() {
		assertTrue(consentPage.isInvalidOtpErrorDisplayed());
	}

	@Then("verify user should be able to enter {string}")
	public void userEnterResendOtp(String otp) {
		consentPage.enterOtp(otp);
	}

	@When("user click on back button in otp screen")
	public void userClickOnBackButton() {
		consentPage.clickOnBackButton();
	}

	String smtpTabHandle;
	String healthPortalTabHandle;

	@Given("user launches SMTP portal")
	public void userNavigatesToSmtpPortalUrl() {
		smtpPage.navigateToSmtpUrl();
		smtpTabHandle = driver.getWindowHandle();
	}

	@Then("navigate to eSignet portal")
	public void userOpensEsignetPortal() {
		driver.switchTo().newWindow(WindowType.TAB);
		smtpPage.navigateToHealthPortalUrl();
		healthPortalTabHandle = driver.getWindowHandle();
	}

	@When("user enter the valid UIN linked with both mobileNumber and email")
	public void userEnterValidUin() {
		String uin = baseTest.getUin();
		consentPage.enterUinOrVid(uin);
	}

	@Then("user switches back to SMTP portal")
	public void userSwitchesBackToSmtp() {
		driver.switchTo().window(smtpTabHandle);
	}

	@Then("switch back to eSignet portal")
	public void userSwitchesBackToHealthPortal() {
		driver.switchTo().window(healthPortalTabHandle);
	}

	@Then("user should get OTP on the registered mobileNumber and email")
	public void verifyUserReceivesNotificationForBoth() {
		smtpPage.isOtpReceivedForMobileNumberAndEmail();
	}

	@When("user enter the single_char VID in the UINorVID text field")
	public void userEnterSingleCharVId() {
		consentPage.enterUinOrVid("3");
	}

	@When("user enter the invalid VID in the UINorVID text field")
	public void userEnterInvalidVid() {
		consentPage.enterUinOrVid("980206143436975");
	}

	@When("user enter the valid VID in the UINorVID text field")
	public void userEnterVid() {
		String vid = baseTest.getVid();
		consentPage.enterUinOrVid(vid);
	}

	@When("user enter the valid VID linked with both mobileNumber and email")
	public void userEnterValidVid() {
		String vid = baseTest.getVid();
		consentPage.enterUinOrVid(vid);
	}
}
