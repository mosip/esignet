package pages;

import java.time.Duration;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;

public class InvalidUrlPage extends BasePage {

	public InvalidUrlPage(WebDriver driver) {
		super(driver);
	}

	@FindBy(xpath = "//div[@class='p-2 mt-1 mb-1 w-full text-center text-sm rounded-lg text-red-700 bg-red-100 undefined']")
	WebElement unableToProcessErrorMsg;

	@FindBy(id = "language_dropdown")
	WebElement languageDropdownInErrorPage;

	@FindBy(id = "signup-url-button")
	WebElement signupUrlButton;

	@FindBy(xpath = "//div[@class='error-page-header']")
	WebElement pageDoesNotExistErrorMsg;

	@FindBy(xpath = "//h1[@class='text-center text-2xl']")
	WebElement pageNotExistError;

	@FindBy(id = "reset-password-button")
	WebElement resetPasswordButton;

	@FindBy(id = "register-button")
	WebElement registerButton;

	@FindBy(xpath = "//div[@class='flex flex-col items-center gap-y-2']")
	WebElement somethingWentWrongErrorMsg;

	@FindBy(id = "proceed-button")
	WebElement proceedButtonAttentionScreen;

	public boolean isUnableToProcessErrorDisplayed() {
		return isElementVisible(unableToProcessErrorMsg, "Verified unable to process error message displayed");
	}

	public void clickOnLanguageDropdownOption() {
		clickOnElement(languageDropdownInErrorPage, "Clicked on language dropdown");
	}

	public boolean isErrorMsgLanguageChanged(String text) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		wait.until(ExpectedConditions.textToBePresentInElement(unableToProcessErrorMsg, text));
		return unableToProcessErrorMsg.getText().contains(text);
	}

	public boolean isEsignetPageRetained() {
		return isElementVisible(signupUrlButton, "Verified esignet page is retained");
	}

	public boolean isPageDoesNotExistErrorMsgDisplayed() {
		return isElementVisible(pageDoesNotExistErrorMsg,
				"Verified page looking for does not exist error is displayed");
	}

	public boolean isPageNotExistErrorScreenDisplayed() {
		return isElementVisible(pageNotExistError, "Verified page not exist error is displayed");
	}

	public boolean isResetPasswordButtonVisible() {
		return isElementVisible(resetPasswordButton, "Verified reset password button is displayed");
	}

	public boolean isRegisterButtonVisible() {
		return isElementVisible(registerButton, "Verified register button is displayed");
	}

	public void clickOnResetPasswordButton() {
		clickOnElement(resetPasswordButton, "Clicked on reset password button");
	}

	public boolean isSomethingWentWrongErrorDisplayed() {
		return isElementVisible(somethingWentWrongErrorMsg, "Verified something went wrong error screen is displayed");
	}

	public boolean isAttentionScreenDisplayed() {
		return isElementVisible(proceedButtonAttentionScreen, "Verified attention screen is displayed");
	}

}