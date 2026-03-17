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

	@Then("verify user navigate to eKYC Process Steps screen")
	public void verifyUserNavigateToEKycProcessStepsScreen() {
		assertTrue("User didn't navigated to eKYC Process Steps screen",
				ekycPage.isEkycProcessStepsScreenLabelDisplayed());
	}

	@Then("user verify the title of step 1 is Choose an eKYC provider")
	public void userVerifyTitleOfStep1IsChooseEkycProvider() {
		assertTrue("Title of the ekyc step 1 not displayed", ekycPage.isEkycStep1TitleChooseEkycProviderDisplayed());
	}

	@Then("user verify that the subtitle of step 1 is displayed in eKYC Process Steps screen")
	public void userVerifyTheSubtitleOfStep1() {
		assertTrue("Subtitle of the ekyc step 1 not displayed", ekycPage.isEkycStep1SubtitleDisplayed());
	}

	@Then("user verify the title of step 2 is Terms And Conditions")
	public void userVerifyTitleOfStep2IsTermsAndConditions() {
		assertTrue("Title of the ekyc step 2 not displayed", ekycPage.isEkycStep2TitleTermsAndConditionsDisplayed());
	}

	@Then("user verify that the subtitle of step 2 is displayed in eKYC Process Steps screen")
	public void userVerifyTheSubtitleOfStep2() {
		assertTrue("Subtitle of the ekyc step 2 not displayed", ekycPage.isEkycStep2SubtitleDisplayed());
	}
}