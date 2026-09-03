package stepdefinitions;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.LivenessCheckPage;
import utils.BaseTestUtil;

public class LivenessCheckStepDefinition {

	public WebDriver driver;
	LivenessCheckPage livenessCheckPage;

	public LivenessCheckStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		livenessCheckPage = new LivenessCheckPage(driver);
	}

	@Then("verify liveness check screen is displayed")
	public void verifyLivenessCheckScreenDisplayed() {
		Assert.assertTrue(livenessCheckPage.isLivenessCheckHeaderDisplayed(),
				"Liveness check screen is not displayed");
	}

	@Then("verify the welcome countdown message is displayed above the video frame on video eKYC screen")
	public void verifyWelcomeCountdownMessageIsDisplayedAboveVideoFrame() {
		Assert.assertTrue(livenessCheckPage.isWelcomeCountdownMessageDisplayed(),
				"Welcome countdown message ('Welcome! Initiating Identity verification process in NN seconds') is not displayed on the video eKYC screen");
	}

	@Then("verify user is able to view themselves on video eKYC screen")
	public void verifyUserIsAbleToViewThemselvesOnVideoEkycScreen() {
		Assert.assertTrue(livenessCheckPage.isUserVisibleInVideoFeed(),
				"User is not able to view themselves via the video on the video eKYC screen");
	}

	@Then("verify the logo is displayed on video eKYC screen")
	public void verifyLogoIsDisplayedOnVideoEkycScreen() {
		Assert.assertTrue(livenessCheckPage.isLogoDisplayed(), "Logo is not displayed on the video eKYC screen");
	}

	@Then("verify the language dropdown is displayed on video eKYC screen")
	public void verifyLanguageDropdownIsDisplayedOnVideoEkycScreen() {
		Assert.assertTrue(livenessCheckPage.isLanguageDropdownDisplayed(),
				"Language dropdown is not displayed on the video eKYC screen");
	}

	@Then("verify the language dropdown is expanded on video eKYC screen")
	public void verifyLanguageDropdownIsExpandedOnVideoEkycScreen() {
		Assert.assertTrue(livenessCheckPage.isLanguageDropdownExpanded(),
				"Language dropdown did not expand on the video eKYC screen");
	}

	@Then("verify the language dropdown is collapsed on video eKYC screen")
	public void verifyLanguageDropdownIsCollapsedOnVideoEkycScreen() {
		Assert.assertTrue(livenessCheckPage.isLanguageDropdownCollapsed(),
				"Language dropdown did not collapse on the video eKYC screen");
	}

	@Then("verify the powered by eSignet footer is displayed on video eKYC screen")
	public void verifyPoweredByEsignetFooterIsDisplayedOnVideoEkycScreen() {
		Assert.assertTrue(livenessCheckPage.isPoweredByFooterDisplayed(),
				"'Powered by eSignet' footer is not displayed on the video eKYC screen");
	}

	@Then("verify onscreen instructions are displayed above the video frame on video eKYC screen")
	public void verifyOnscreenInstructionsAreDisplayedAboveVideoFrame() {
		Assert.assertTrue(livenessCheckPage.isInstructionsDisplayedAboveVideoFrame(),
				"Onscreen instructions are not displayed above the video frame on the video eKYC screen");
	}

	@Then("user disconnects the network")
	public void userDisconnectsTheNetwork() {
		livenessCheckPage.disconnectNetwork();
	}

	@Then("user reconnects the network")
	public void userReconnectsTheNetwork() {
		livenessCheckPage.reconnectNetwork();
	}

	@Then("verify eKYC error message is displayed")
	public void verifyEkycErrorMessageIsDisplayed() {
		Assert.assertTrue(livenessCheckPage.isEkycErrorDisplayed(), "eKYC error message is not displayed");
	}

	@Then("clicks on okay button in eKYC error screen")
	public void clicksOnOkayButtonInEkycErrorScreen() {
		livenessCheckPage.clickOkayOnEkycError();
	}

	@Then("verify eKYC failure popup shows header {string}, message {string} and button {string}")
	public void verifyEkycFailurePopupContent(String header, String message, String buttonLabel) {
		Assert.assertTrue(livenessCheckPage.isEkycFailurePopupContentCorrect(header, message, buttonLabel),
				"eKYC failure popup did not show the expected header/message/button text");
	}

	@Then("verify network dropped message is displayed")
	public void verifyNetworkDroppedMessageIsDisplayed() {
		Assert.assertTrue(livenessCheckPage.isNetworkDroppedMessageDisplayed(),
				"Network dropped message is not displayed");
	}

	@Then("user starts monitoring slot availability requests")
	public void userStartsMonitoringSlotAvailabilityRequests() {
		livenessCheckPage.startCapturingSlotRequests();
	}

	@Then("verify slot availability is checked every 6 seconds for a maximum of 10 attempts")
	public void verifySlotAvailabilityPollingContract() {
		Assert.assertTrue(livenessCheckPage.isSlotPollingContractValid(),
				"Slot availability polling did not match the expected 6s interval / 10 attempt max contract");
	}

	@Then("verify video eKYC screen is mobile responsive")
	public void verifyVideoEkycScreenIsMobileResponsive() {
		Assert.assertTrue(livenessCheckPage.isMobileResponsive(),
				"Video eKYC verification screen is not mobile responsive across viewport sizes");
	}

	@When("user captures the video eKYC screen content before language change")
	public void userCapturesVideoEkycScreenContentBeforeLanguageChange() {
		livenessCheckPage.captureContentSnapshot();
	}

	@When("user clicks on language dropdown in video eKYC screen")
	public void userClicksOnLanguageDropdownInVideoEkycScreen() {
		livenessCheckPage.clickOnLanguageDropdown();
	}

	@When("user selects khmer language in video eKYC screen")
	public void userSelectsKhmerLanguageInVideoEkycScreen() {
		livenessCheckPage.selectLanguage("khm");
	}

	@When("user selects the mandatory language in video eKYC screen")
	public void userSelectsMandatoryLanguageInVideoEkycScreen() {
		livenessCheckPage.selectLanguage(BaseTestUtil.getThreadLocalLanguage());
	}

	@Then("verify video eKYC screen content and language are updated to khmer")
	public void verifyVideoEkycScreenContentAndLanguageAreUpdatedToKhmer() {
		Assert.assertTrue(livenessCheckPage.isContentDisplayedInLanguage("khm"),
				"Video eKYC screen content/language was not updated to Khmer");
	}

	@Then("verify video eKYC screen content is displayed in english language")
	public void verifyVideoEkycScreenContentIsDisplayedInEnglishLanguage() {
		Assert.assertTrue(livenessCheckPage.isContentDisplayedInLanguage("eng"),
				"Video eKYC screen content is not displayed in English language");
	}

	@Then("verify video eKYC screen content and language are reverted to the mandatory language")
	public void verifyVideoEkycScreenContentAndLanguageAreRevertedToMandatoryLanguage() {
		Assert.assertTrue(livenessCheckPage.isContentDisplayedInLanguage(BaseTestUtil.getThreadLocalLanguage()),
				"Video eKYC screen content/language was not reverted to the mandatory language");
	}

	@When("user disables camera access on video eKYC screen")
	public void userDisablesCameraAccessOnVideoEkycScreen() {
		livenessCheckPage.disableCameraAccessAtRuntime();
	}

	@When("user enables camera access on video eKYC screen")
	public void userEnablesCameraAccessOnVideoEkycScreen() {
		livenessCheckPage.enableCameraAccessAtRuntime();
	}

	@Then("verify session timeout message is displayed after disabling camera access")
	public void verifySessionTimeoutMessageIsDisplayedAfterDisablingCameraAccess() {
		Assert.assertTrue(livenessCheckPage.waitForSessionTimeoutMessage(),
				"Session timeout message was not displayed after disabling camera access on video eKYC screen");
	}

	@Then("verify user is redirected to relying party with verification incomplete error")
	public void verifyUserIsRedirectedToRelyingPartyWithVerificationIncompleteError() {
		Assert.assertTrue(livenessCheckPage.waitForRedirectWithVerificationIncompleteError(),
				"User was not redirected to relying party with the expected verification_incomplete error");
	}

	@Then("verify solid colors are displayed across the full screen on video eKYC screen")
	public void verifySolidColorsAreDisplayedAcrossFullScreen() {
		Assert.assertTrue(livenessCheckPage.isSolidColorFrameCyclingAcrossFullScreen(),
				"Solid, cycling colors were not observed across the full screen on the video eKYC screen");
	}

	@Then("verify instructions and video frame are aligned properly on video eKYC screen")
	public void verifyInstructionsAndVideoFrameAreAlignedProperly() {
		Assert.assertTrue(livenessCheckPage.areInstructionsAndVideoFrameAligned(),
				"Instructions and video frame are not aligned properly on the video eKYC screen");
	}

}
