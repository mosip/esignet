package stepdefinitions;

import org.testng.Assert;

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
		Assert.assertTrue(ekycPage.isEkycProcessStepsScreenLabelDisplayed(), "User didn't navigated to eKYC Process Steps screen");
	}

	@Then("user verify the title of step 1 is Choose an eKYC provider")
	public void userVerifyTitleOfStep1IsChooseEkycProvider() {
		Assert.assertTrue(ekycPage.isEkycStep1TitleChooseEkycProviderDisplayed(), "Title of the ekyc step 1 not displayed");
	}

	@Then("user verify that the subtitle of step 1 is displayed in eKYC Process Steps screen")
	public void userVerifyTheSubtitleOfStep1() {
		Assert.assertTrue(ekycPage.isEkycStep1SubtitleDisplayed(), "Subtitle of the ekyc step 1 not displayed");
	}

	@Then("user verify the title of step 2 is Terms And Conditions")
	public void userVerifyTitleOfStep2IsTermsAndConditions() {
		Assert.assertTrue(ekycPage.isEkycStep2TitleTermsAndConditionsDisplayed(), "Title of the ekyc step 2 not displayed");
	}

	@Then("user verify that the subtitle of step 2 is displayed in eKYC Process Steps screen")
	public void userVerifyTheSubtitleOfStep2() {
		Assert.assertTrue(ekycPage.isEkycStep2SubtitleDisplayed(), "Subtitle of the ekyc step 2 not displayed");
	}
}