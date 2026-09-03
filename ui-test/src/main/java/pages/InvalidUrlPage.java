package pages;

import java.time.Duration;

import org.openqa.selenium.By;
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

	@FindBy(xpath = "//div[@class='error-page-detail']")
	WebElement unableToProcessErrorMsg;

	@FindBy(id = "language_dropdown")
	WebElement languageDropdownInErrorPage;

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
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20)).until(driverInstance -> {
				try {
					return isLoginUiVisibleNow(driverInstance) && !isAuthorizeErrorLanding(driverInstance);
				} catch (Exception ignored) {
					return false;
				}
			});
			utils.ExtentReportManager.getTest().log(com.aventstack.extentreports.Status.INFO,
					"Verified esignet page is retained - Verified visibility");
			return true;
		} catch (org.openqa.selenium.TimeoutException e) {
			utils.ExtentReportManager.getTest().log(com.aventstack.extentreports.Status.INFO,
					"Element not visible: login page after URL change (landed on " + driver.getCurrentUrl() + ")");
			return false;
		}
	}

	private boolean isLoginUiVisibleNow(WebDriver webDriver) {
		return !webDriver.findElements(By.id("language_selection")).isEmpty()
				|| !webDriver.findElements(By.cssSelector("[id^='acr_']")).isEmpty()
				|| !webDriver.findElements(By.id("username_input")).isEmpty()
				|| !webDriver.findElements(By.cssSelector("nav button[aria-haspopup='listbox']")).isEmpty();
	}

	private boolean isAuthorizeErrorLanding(WebDriver webDriver) {
		String url = webDriver.getCurrentUrl();
		if (url != null && url.contains("error=invalid_request")) {
			return true;
		}
		return !webDriver.findElements(By.className("error-page-header")).isEmpty()
				|| !webDriver.findElements(By.xpath("//*[contains(text(),'Something went wrong')]")).isEmpty();
	}

	public boolean isPageDoesNotExistErrorMsgDisplayed() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> isPageNotFoundVisibleNow(d));
			utils.ExtentReportManager.getTest().log(com.aventstack.extentreports.Status.INFO,
					"Verified page looking for does not exist error is displayed - Verified visibility");
			return true;
		} catch (org.openqa.selenium.TimeoutException e) {
			utils.ExtentReportManager.getTest().log(com.aventstack.extentreports.Status.INFO,
					"Page-not-found not visible (landed on " + driver.getCurrentUrl() + ")");
			return isPageNotFoundVisibleNow(driver);
		}
	}

	private boolean isPageNotFoundVisibleNow(WebDriver webDriver) {
		if (!webDriver.findElements(By.className("error-page-header")).isEmpty()) {
			return true;
		}
		if (!webDriver.findElements(By.xpath("//h1[@class='text-center text-2xl']")).isEmpty()) {
			return true;
		}
		try {
			String body = webDriver.findElement(By.tagName("body")).getText().toLowerCase();
			String source = webDriver.getPageSource().toLowerCase();
			return body.contains("page not found") || body.contains("does not exist")
					|| source.contains("404 page not found");
		} catch (Exception ignored) {
			return false;
		}
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

	public boolean isUnauthorizedErrorDisplayed() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15))
					.pollingEvery(Duration.ofMillis(500))
					.until(driverInstance -> isUnauthorizedErrorVisibleNow());
			return true;
		} catch (org.openqa.selenium.TimeoutException e) {
			return isUnauthorizedErrorVisibleNow();
		}
	}

	private boolean isUnauthorizedErrorVisibleNow() {
		if (isPageNotFoundVisibleNow(driver)) {
			return true;
		}
		if (isSomethingWentWrongErrorDisplayed()) {
			return true;
		}
		if (isUnableToProcessErrorDisplayed()) {
			return true;
		}
		String pageText = driver.findElement(By.tagName("body")).getText().toLowerCase();
		return pageText.contains("unauthorized") || pageText.contains("something went wrong")
				|| pageText.contains("unable to process");
	}

	public boolean isAttentionScreenDisplayed() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(30)).until(this::isAttentionOrConsentVisibleNow);
			utils.ExtentReportManager.getTest().log(com.aventstack.extentreports.Status.INFO,
					"Verified attention screen is displayed - Verified visibility");
			return true;
		} catch (org.openqa.selenium.TimeoutException e) {
			utils.ExtentReportManager.getTest().log(com.aventstack.extentreports.Status.INFO,
					"Attention/consent screen not visible (landed on " + driver.getCurrentUrl() + ")");
			return isAttentionOrConsentVisibleNow(driver);
		}
	}

	private boolean isAttentionOrConsentVisibleNow(WebDriver webDriver) {
		return isDisplayedNow(webDriver, By.id("action_allow"))
				|| isDisplayedNow(webDriver, By.id("block_consent"))
				|| isDisplayedNow(webDriver, By.id("text_consent_title"))
				|| isDisplayedNow(webDriver, By.id("proceed-button"));
	}

	private boolean isDisplayedNow(WebDriver webDriver, By locator) {
		try {
			java.util.List<WebElement> found = webDriver.findElements(locator);
			return !found.isEmpty() && found.get(0).isDisplayed();
		} catch (Exception ignored) {
			return false;
		}
	}

}
