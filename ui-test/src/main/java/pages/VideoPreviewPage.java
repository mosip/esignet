package pages;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import base.BasePage;

public class VideoPreviewPage extends BasePage {

	public VideoPreviewPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "video-preview-header")
	WebElement keyInformation;

	@FindBy(id = "cancel-preview-button")
	WebElement cancelButton;

	@FindBy(id = "proceed-preview-button")
	WebElement proceedButton;

	@FindBy(xpath = "//div[contains(@class,'scrollable-div')]")
	private WebElement instructionContainer;

	@FindBy(id = "stay-button")
	WebElement stayButton;

	@FindBy(id = "dismiss-button")
	WebElement discontinueButton;

	@FindBy(xpath = "//div[contains(@class,'video-message')]")
	private WebElement loadingText;

	@FindBy(id = "sign-in-with-esignet")
	WebElement signWithEsignetButton;

	@FindBy(xpath = "//span[contains(@class,'video-preview-content')]")
	private WebElement listOfInstructions;

	public boolean isOnKeyInformationScreen() {
		return keyInformation.isDisplayed();
	}

	public boolean isHeaderDisplayedOnScreen() {
		return keyInformation.isDisplayed();
	}

	public boolean isCancelButtonDisplayed() {
		return isElementVisible(cancelButton, "Verified cancel button is visible");
	}

	public boolean isProceedButtonDisplayed() {
		return isElementVisible(proceedButton, "Verified proceed button is visible");
	}

	public boolean isProceedButtonEnabled() {
		return isButtonEnabled(proceedButton, "Verified cancel button is enabled");
	}

	public void clickOnProceedButton() {
		clickOnElement(proceedButton, "Clicked on proceed button");
	}

	public boolean isScrollPresent() {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		Long scrollHeight = (Long) js.executeScript("return arguments[0].scrollHeight;", instructionContainer);
		Long clientHeight = (Long) js.executeScript("return arguments[0].clientHeight;", instructionContainer);
		return scrollHeight > clientHeight;
	}

	public void clickOnCancelButton() {
		clickOnElement(cancelButton, "Clicked on canecl button");
	}

	public boolean isAttentionPopupDisplayed() {
		return isElementVisible(instructionContainer, "Verified attention popup is visible");
	}

	public void clickOnStayButton() {
		clickOnElement(stayButton, "Clicked on stay button");
	}

	public void clickOnDiscontinueButton() {
		clickOnElement(discontinueButton, "Clicked on discontinue button");
	}

	public boolean isLoadingMessageDisplayed() {
		return isElementVisible(loadingText, "Verified loading message is visible");
	}

	public void clickOnSignWithEsignetButton() {
		clickOnElement(signWithEsignetButton, "Clicked on sign with esignet button");
	}

	public boolean isListOfInstructionsDisplayed() {
		return isElementVisible(listOfInstructions, "Verified list of instructions is visible");
	}

}
