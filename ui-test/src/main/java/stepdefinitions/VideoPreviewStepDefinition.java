package stepdefinitions;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;
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

	@Then("verify user should be navigated to video preview screen page")
	public void userNavigateToVideoPreviewScreen() {
		Assert.assertTrue(videoPreviewPage.isVideoPreviewScreenDisplayed(),
				"User didn't navigated to video preview screen page");
	}

	@Then("verify key information header is displayed in video preview screen page")
	public void verifyKeyInformationHeaderDisplayedOnScreen() {
		Assert.assertTrue(videoPreviewPage.isKeyInformationHeaderDisplayed(),
				"Key information header is not displayed in video preview screen page");
	}

	@Then("verify proceed button present in video preview screen page")
	public void verifyProceedButtonDisplayedOnVideoPreviewScreen() {
		Assert.assertTrue(videoPreviewPage.isProceedButtonDisplayed(),
				"Proceed button is not displayed in video preview screen page");
	}

	@Then("verify cancel button present in video preview screen page")
	public void verifyCancelButtonDisplayedOnVideoPreviewScreen() {
		Assert.assertTrue(videoPreviewPage.isCancelButtonDisplayed(),
				"Cancel button is not displayed in video preview screen page");
	}

	@Then("verify proceed button enable after camera access")
	public void verifyProceedButtonEnabledOnVideoPreviewScreen() {
		Assert.assertTrue(videoPreviewPage.isProceedButtonEnabled(),
				"Proceed button is not Enabled after camera access");
	}

	@Then("verify scrollable present in video preview screen page")
	public void verifyScrollOptionIsDisplayedOnVideoPreviewScreen() {
		Assert.assertTrue(videoPreviewPage.isScrollOptionPresent(),
				"Scroll option is not present in video preview screen page");
	}

	@Then("clicks on cancel button in video preview screen page")
	public void clicksOnCaneclButtonInVideoPreviewScreen() {
		videoPreviewPage.clickOnCancelButton();
	}

	@Then("verify attention warning popup displayed in video preview screen page")
	public void verifyAttentionWarningPopupDisplayedInVideoPreviewScreen() {
		Assert.assertTrue(videoPreviewPage.isAttentionWarningPopupDisplayed(),
				"The attention warning popup is not displayed when clicked on cancel");
	}

	@Then("clicks on stay button in attention warning popup")
	public void clickOnStayButtonInAttentionWarningPopup() {
		videoPreviewPage.clickOnStayButtonInAttentionWarningPopup();
	}

	@Then("clicks on discontinue button in attention warning popup")
	public void clickOnDiscontinueButtonInAttentionWarningPopup() {
		videoPreviewPage.clickOnDiscontinueButtonInAttentionWarningPopup();
	}

	@Then("verify loading screen message displayed in video capture screen page")
	public void verifyLoadingScreenMessageDisplayedInVideoCaptureScreen() {
		Assert.assertTrue(videoPreviewPage.isLoadingScreenMessageDisplayed(),
				"The loading screen message not displayed in video capture screen page");
	}

	@Then("clicks on sign in with esignet button in login page")
	public void clickOnSignInWithEsignetButtonInLoginPage() {
		videoPreviewPage.clickOnSignInWithEsignetButton();
	}

	@Then("verify list of instructions displayed in video preview screen page")
	public void verifyListOfInstructionsDisplayedInVideoPreviewScreen() {
		Assert.assertTrue(videoPreviewPage.isListOfInstructionsDisplayed(),
				"The list of instructions not displayed in video preview screen page");
	}

}