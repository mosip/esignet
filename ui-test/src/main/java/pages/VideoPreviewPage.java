package pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import base.BasePage;

public class VideoPreviewPage extends BasePage {

	public VideoPreviewPage(WebDriver driver) {
		super(driver);
	}

	@FindBy(id = "video-preview-header")
	WebElement keyInformation;

	@FindBy(id = "cancel-preview-button")
	WebElement cancelButton;

	@FindBy(id = "proceed-preview-button")
	WebElement proceedButton;

	@FindBy(xpath = "//div[contains(@class,'scrollable-div')]")
	private WebElement scrollOption;

	@FindBy(id = "stay-button")
	WebElement stayButton;

	@FindBy(id = "dismiss-button")
	WebElement discontinueButton;

	@FindBy(xpath = "//div[contains(@class,'video-message')]")
	WebElement loadingScreenMessage;

	@FindBy(id = "sign-in-with-esignet")
	WebElement signInWithEsignetButton;

	@FindBy(xpath = "//span[contains(@class,'video-preview-content')]")
	WebElement listOfInstructions;

	@FindBy(xpath = "//div[@role='alertdialog']//h2[contains(@class,'font-semibold')]")
	WebElement attentionWarningPopup;

	public boolean isVideoPreviewScreenDisplayed() {
		return isElementVisible(keyInformation, "Verified video preview screen is visible");
	}

	public boolean isKeyInformationHeaderDisplayed() {
		return isElementVisible(keyInformation, "Verified keyInformation header is visible");
	}

	public boolean isCancelButtonDisplayed() {
		return isElementVisible(cancelButton, "Verified cancel button is visible");
	}

	public boolean isProceedButtonDisplayed() {
		return isElementVisible(proceedButton, "Verified proceed button is visible");
	}

	public boolean isProceedButtonEnabled() {
		return isButtonEnabled(proceedButton, "Verified proceed button is enabled");
	}

	public boolean isScrollOptionPresent() {
		return isElementVisible(scrollOption, "Verified scroll option is visible");
	}

	public void clickOnCancelButton() {
		clickOnElement(cancelButton, "Clicked on cancel button");
	}

	public boolean isAttentionWarningPopupDisplayed() {
		return isElementVisible(attentionWarningPopup, "Verified attention warning popup is visible");
	}

	public void clickOnStayButtonInAttentionWarningPopup() {
		clickOnElement(stayButton, "Clicked on stay button in attention warning popup");
	}

	public void clickOnDiscontinueButtonInAttentionWarningPopup() {
		clickOnElement(discontinueButton, "Clicked on discontinue button in attention warning popup");
	}

	public boolean isLoadingScreenMessageDisplayed() {
		return isElementVisible(loadingScreenMessage, "Verified loading screen message is visible");
	}

	public void clickOnSignInWithEsignetButton() {
		clickOnElement(signInWithEsignetButton, "Clicked on sign in with esignet button");
	}

	public boolean isListOfInstructionsDisplayed() {
		return isElementVisible(listOfInstructions, "Verified list of instructions is visible");
	}
}
