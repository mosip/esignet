package pages;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;

public class BrowserCompatibilityPage extends BasePage {

	public BrowserCompatibilityPage(WebDriver driver) {
		super(driver);
	}

	public void setUserAgentOverride(String userAgent) {
		if (!(driver instanceof ChromeDriver chromeDriver)) {
			return;
		}
		Map<String, Object> params = new HashMap<>();
		params.put("userAgent", userAgent);
		chromeDriver.executeCdpCommand("Network.setUserAgentOverride", params);
	}

	@FindBy(xpath = "//*[normalize-space(text())='Alert!']")
	WebElement header;

	@FindBy(xpath = "//*[contains(text(),\"unsupported browser\")]")
	WebElement subHeader;

	@FindBy(xpath = "//button[normalize-space(text())='Okay']")
	WebElement okayButton;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement logo;

	@FindBy(id = "language_selection")
	WebElement languageDropdown;

	@FindBy(xpath = "//*[contains(text(),'Powered by') and contains(.,'eSignet')]")
	WebElement poweredByFooter;

	public boolean isHeaderDisplayed() {
		return isElementVisible(header, "Verified 'Alert!' header is displayed on browser compatibility screen");
	}

	public boolean isSubHeaderDisplayed() {
		return isElementVisible(subHeader, "Verified subheader is displayed on browser compatibility screen");
	}

	public String getSubHeaderText() {
		waitForElementVisible(subHeader);
		return subHeader.getText().trim();
	}

	public boolean isOkayButtonDisplayed() {
		return isElementVisible(okayButton, "Verified Okay button is displayed on browser compatibility screen");
	}

	public boolean isOkayButtonEnabled() {
		return isButtonEnabled(okayButton, "Verified Okay button is enabled on browser compatibility screen");
	}

	public boolean isLogoDisplayed() {
		return isElementVisible(logo, "Verified logo is displayed on browser compatibility screen");
	}

	public boolean isLanguageDropdownDisplayed() {
		return isElementVisible(languageDropdown,
				"Verified language dropdown is displayed on browser compatibility screen");
	}

	public boolean isPoweredByFooterDisplayed() {
		return isElementVisible(poweredByFooter,
				"Verified 'Powered by eSignet' footer is displayed on browser compatibility screen");
	}

	private static final String INCOMPATIBLE_BROWSER_ERROR_CODE = "incompatible_browser";
	private static final String INCOMPATIBLE_BROWSER_MESSAGE = "We're sorry! Please upgrade to the latest version of the browser & try again.";
	private static final Duration RELYING_PARTY_REDIRECT_WAIT = Duration.ofSeconds(30);

	public boolean waitForIncompatibleBrowserRedirect() {
		WebDriverWait wait = new WebDriverWait(driver, RELYING_PARTY_REDIRECT_WAIT);
		try {
			wait.until(d -> d.getCurrentUrl().contains("error=" + INCOMPATIBLE_BROWSER_ERROR_CODE));
		} catch (TimeoutException e) {
			return false;
		}

		String pageText = driver.findElement(By.tagName("body")).getText();
		return pageText.contains(INCOMPATIBLE_BROWSER_MESSAGE);
	}

	public void clickOnOkayButton() {
		clickOnElement(okayButton, "Clicked on Okay button on browser compatibility screen");
	}
}
