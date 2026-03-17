package stepdefinitions;

import static org.junit.Assert.assertTrue;

import org.openqa.selenium.WebDriver;
import org.apache.log4j.Logger;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.EkycPage;

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
	assertTrue(ekycPage.isEkycStep1TitleChooseEkycProvider());
	}
	
	@Then("Verify that the subtitle of step 1 is displayed in eKYC Process Steps screen")
	public void verifyTheSubtitleOfStep1() {
	assertTrue(ekycPage.isEkycStep1Subtitle());
	}	
	
	@Then("Verify the title of step 2 is Terms And Conditions")
	public void verifyTheTitleOfStep2IsTermsAndConditions() {
	assertTrue(ekycPage.isEkycStep2TitleTermsAndConditions());
	}
	
	@Then("Verify that the subtitle of step 2 is displayed in eKYC Process Steps screen")
	public void verifyTheSubtitleOfStep2() {
	assertTrue(ekycPage.isEkycStep2Subtitle());
	}	
}