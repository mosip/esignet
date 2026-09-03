package stepdefinitions;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.MultiLanguagePage;
import pages.VideoPreviewPage;

public class VideoPreviewStepDefinition {

	public WebDriver driver;
	VideoPreviewPage videoPreviewPage;
	MultiLanguagePage multiLanguagePage;

	public VideoPreviewStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		videoPreviewPage = new VideoPreviewPage(driver);
		multiLanguagePage = new MultiLanguagePage(driver);
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

	@Then("verify camera permission state is {string}")
	public void verifyCameraPermissionState(String expectedState) {
		Assert.assertEquals(videoPreviewPage.getCameraPermissionState(), expectedState,
				"Camera permission state did not match");
	}

	@Then("verify camera access disabled message is displayed")
	public void verifyCameraAccessDisabledMessageIsDisplayed() {
		Assert.assertTrue(videoPreviewPage.isCameraAccessDisabledHeaderDisplayed(),
				"Camera access disabled message is not displayed in video preview screen page");
	}

	@Then("verify camera access disabled subtitle is displayed")
	public void verifyCameraAccessDisabledSubtitleIsDisplayed() {
		Assert.assertTrue(videoPreviewPage.isCameraAccessDisabledSubHeaderDisplayed(),
				"Camera access disabled subtitle is not displayed in video preview screen page");
	}

	@Then("verify proceed button is disabled in video preview screen page")
	public void verifyProceedButtonIsDisabledInVideoPreviewScreenPage() {
		Assert.assertTrue(videoPreviewPage.isProceedButtonDisabled(),
				"Proceed button is not disabled in video preview screen page");
	}

	@Then("user grants camera access from browser settings")
	public void userGrantsCameraAccessFromBrowserSettings() {
		videoPreviewPage.grantCameraAccessAtRuntime();
	}

	@Then("user refreshes the browser")
	public void userRefreshesTheBrowser() {
		videoPreviewPage.refreshBrowser();
	}

	@When("user navigates back in the browser and a leave site prompt should appear")
	public void userNavigatesBackAndLeaveSitePromptShouldAppear() {
		driver.navigate().back();
		Assert.assertTrue(videoPreviewPage.isLeaveSitePromptDisplayed(10),
				"The 'Leave site?' prompt did not appear after navigating back from the video preview screen");
	}

	@Then("verify user is no longer on the video preview screen")
	public void verifyUserIsNoLongerOnTheVideoPreviewScreen() {
		Assert.assertFalse(videoPreviewPage.isVideoPreviewScreenDisplayed(),
				"User is still on the video preview screen");
	}

	@When("select the khmer language")
	public void selectTheKhmerLanguage() {
		multiLanguagePage.clickOnLanguage(utils.LanguageUtil.getDisplayName("khm"));
	}

	@When("select the english language")
	public void selectTheEnglishLanguage() {
		multiLanguagePage.clickOnLanguage(utils.LanguageUtil.getDisplayName("eng"));
	}

	@Then("verify video preview screen content is displayed in khmer language")
	public void verifyVideoPreviewScreenContentDisplayedInKhmer() {
		Assert.assertTrue(videoPreviewPage.isDisplayedInLanguage("khm"),
				"Video preview screen content is not displayed in Khmer language");
	}

}
