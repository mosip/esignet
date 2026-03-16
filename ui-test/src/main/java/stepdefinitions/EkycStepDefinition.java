package stepdefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.apache.log4j.Logger;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.mosip.testrig.apirig.testrunner.OTPListener;
import pages.ConsentPage;
import pages.EkycPage;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.EsignetUtil;
import utils.EsignetUtil.RegisteredDetails;

public class EkycStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(EkycStepDefinition.class);
	EkycPage ekycPage;

	public EkycStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		ekycPage = new EkycPage(driver);
	}
    
	@Then("Verify user navigate to eKYC Process Steps screen")
	public void verifyUserNavigateToEKycProcessStepsScreen() {
	    assertTrue(ekycPage.isEkycProcessStepsScreenLabelDisplayed());
	}
	
	@Then("Verify the title of step 1 is Choose an eKYC provider")
	public void verifyTheTitleOfStep1IsChooseEkycProvider() {
	assertTrue(ekycPage.isVerifyStep1TitleChooseEkycProvider());
	}
	
	@Then("Verify that the subtitle of step 1 is displayed in eKYC Process Steps screen")
	public void verifyTheSubtitleOfStep1() {
	assertTrue(ekycPage.isVerifyStep1Subtitle());
	}	
	
	@Then("Verify the title of step 2 is Choose an eKYC provider")
	public void verifyTheTitleOfStep2IsChooseEkycProvider() {
	assertTrue(ekycPage.isVerifyStep2TitleChooseEkycProvider());
	}
	
	@Then("Verify that the subtitle of step 2 is displayed in eKYC Process Steps screen")
	public void verifyTheSubtitleOfStep2() {
	assertTrue(ekycPage.isVerifyStep2Subtitle());
	}	
}