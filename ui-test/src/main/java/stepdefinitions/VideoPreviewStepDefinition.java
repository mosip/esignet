package stepdefinitions;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.apache.log4j.Logger;

import base.BaseTest;
import io.cucumber.java.en.Then;
import pages.VideoPreviewPage;
import utils.EsignetUtil;

public class VideoPreviewStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(VideoPreviewStepDefinition.class);
	VideoPreviewPage videoPreviewPage;

	public VideoPreviewStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		videoPreviewPage = new VideoPreviewPage(driver);
	}

	private boolean notApplicableUnderMockPlugin(String featureDescription) {
		return EsignetUtil.notApplicableUnderMockPlugin(featureDescription, logger);
	}

	@Then("verify user should be navigated to video preview screen page")
	public void userNavigateToVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isVideoPreviewScreenDisplayed(),
				"User didn't navigated to video preview screen page");
	}

	@Then("verify key information header is displayed in video preview screen page")
	public void verifyKeyInformationHeaderDisplayedOnScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's key information header")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isKeyInformationHeaderDisplayed(),
				"Key information header is not displayed in video preview screen page");
	}

	@Then("verify proceed button present in video preview screen page")
	public void verifyProceedButtonDisplayedOnVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's proceed button")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isProceedButtonDisplayed(),
				"Proceed button is not displayed in video preview screen page");
	}

	@Then("verify cancel button present in video preview screen page")
	public void verifyCancelButtonDisplayedOnVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's cancel button")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isCancelButtonDisplayed(),
				"Cancel button is not displayed in video preview screen page");
	}

	@Then("verify proceed button enable after camera access")
	public void verifyProceedButtonEnabledOnVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's proceed button")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isProceedButtonEnabled(),
				"Proceed button is not Enabled after camera access");
	}

	@Then("verify scrollable present in video preview screen page")
	public void verifyScrollOptionIsDisplayedOnVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's scroll option")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isScrollOptionPresent(),
				"Scroll option is not present in video preview screen page");
	}

	@Then("clicks on cancel button in video preview screen page")
	public void clicksOnCaneclButtonInVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's cancel button")) {
			return;
		}
		videoPreviewPage.clickOnCancelButton();
	}

	@Then("verify attention warning popup displayed in video preview screen page")
	public void verifyAttentionWarningPopupDisplayedInVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's attention warning popup")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isAttentionWarningPopupDisplayed(),
				"The attention warning popup is not displayed when clicked on cancel");
	}

	@Then("clicks on stay button in attention warning popup")
	public void clickOnStayButtonInAttentionWarningPopup() {
		if (notApplicableUnderMockPlugin("the video preview screen's attention warning popup stay button")) {
			return;
		}
		videoPreviewPage.clickOnStayButtonInAttentionWarningPopup();
	}

	@Then("clicks on discontinue button in attention warning popup")
	public void clickOnDiscontinueButtonInAttentionWarningPopup() {
		if (notApplicableUnderMockPlugin("the video preview screen's attention warning popup discontinue button")) {
			return;
		}
		videoPreviewPage.clickOnDiscontinueButtonInAttentionWarningPopup();
	}

	@Then("verify loading screen message displayed in video capture screen page")
	public void verifyLoadingScreenMessageDisplayedInVideoCaptureScreen() {
		if (notApplicableUnderMockPlugin("the video capture screen's loading message")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isLoadingScreenMessageDisplayed(),
				"The loading screen message not displayed in video capture screen page");
	}

	@Then("clicks on sign in with esignet button in login page")
	public void clickOnSignInWithEsignetButtonInLoginPage() {
		if (notApplicableUnderMockPlugin("the relying party's sign-in-with-esignet button")) {
			return;
		}
		videoPreviewPage.clickOnSignInWithEsignetButton();
	}

	@Then("verify list of instructions displayed in video preview screen page")
	public void verifyListOfInstructionsDisplayedInVideoPreviewScreen() {
		if (notApplicableUnderMockPlugin("the video preview screen's list of instructions")) {
			return;
		}
		Assert.assertTrue(videoPreviewPage.isListOfInstructionsDisplayed(),
				"The list of instructions not displayed in video preview screen page");
	}

}
