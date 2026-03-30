package stepdefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openqa.selenium.WebDriver;

import org.apache.log4j.Logger;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.VideoPreviewPage;

public class VideoPreviewStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(VideoPreviewStepDefinition.class);
	VideoPreviewPage videoPreviewPage;

	public VideoPreviewStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		videoPreviewPage = new VideoPreviewPage(driver);
	}

	@Then("verify user should be navigated to pre-verification guide or video preview key information screen page")
	public void userGoesToPreVerificationGuideOrVideoPreviewKeyInformationScreen() {
		assertTrue(videoPreviewPage.isOnKeyInformationScreen());
	}

	@Then("verify header is displayed in pre-verification guide screen page")
	public void isHeaderDisplayedOnScreen() {
		assertTrue(videoPreviewPage.isOnKeyInformationScreen());
	}

	@Then("verify proceed button present in pre-verification guide screen page")
	public void isProceedButtonDisplayedOnScreen() {
		assertTrue(videoPreviewPage.isProceedButtonDisplayed());
	}

	@Then("verify cancel button present in pre-verification guide screen page")
	public void isCancelButtonDisplayedOnScreen() {
		assertTrue(videoPreviewPage.isCancelButtonDisplayed());
	}

	@Then("verify proceed button enable after camera access")
	public void isProceedButtonEnabledOnScreen() {
		assertTrue(videoPreviewPage.isProceedButtonEnabled());
	}

	@Then("verify scrollable present in pre-verification guide screen page")
	public void isScrollPresent() {
		assertTrue(videoPreviewPage.isScrollPresent());
	}

	@Then("clicks on cancel button in pre-verification guide screen page")
	public void clicksOnCaneclButtonInPreverificationGuideScreenPage() {
		videoPreviewPage.clickOnCancelButton();
	}

	@Then("verify attention popup displayed in pre-verification guide screen page")
	public void verifyAttentionPopupDisplayed() {
		assertTrue(videoPreviewPage.isAttentionPopupDisplayed());
	}

	@Then("clicks on stay button in pre-verification guide screen page")
	public void clickOnStayButtonInPreverificationGuideScreenPage() {
		videoPreviewPage.clickOnStayButton();
	}

	@Then("clicks on discontinue button in pre-verification guide screen page")
	public void clickOnDiscontinueButtonInPreverificationGuideScreenPage() {
		videoPreviewPage.clickOnDiscontinueButton();
	}

	@Then("clicks on proceed button in pre-verification guide screen page")
	public void clickOnProceedButtonInPreverificationGuideScreenPage() {
		videoPreviewPage.clickOnProceedButton();
	}

	@Then("verify loading message displayed in pre-verification guide screen page")
	public void verifyLoadingMessageDisplayed() {
		assertTrue(videoPreviewPage.isLoadingMessageDisplayed());
	}

	@Then("clicks on sign in with esignet button in login page")
	public void clickOnSignWithEsignetButtonInLoginPage() {
		videoPreviewPage.clickOnSignWithEsignetButton();
	}

	@Then("verify list of instructions displayed in pre-verification guide screen page")
	public void verifyListOfInstructionsDisplayed() {
		assertTrue(videoPreviewPage.isListOfInstructionsDisplayed());
	}

}