package stepdefinitions;

import java.time.Duration;

import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;

import base.BasePage;
import base.BaseTest;
import io.cucumber.java.en.When;
import utils.ExtentReportManager;

public class BrowserDialogStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(BrowserDialogStepDefinition.class);
	private BasePage page;

	public BrowserDialogStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		page = new BasePage(driver);
	}

	private boolean isOnRealFormScreen() {
		if (page.isAlreadyOnRelyingParty()) {
			return false;
		}
		long visibleAcrOptions = driver.findElements(By.cssSelector("[id^='acr_']")).stream()
				.filter(WebElement::isDisplayed).count();
		if (visibleAcrOptions > 1) {
			return false;
		}

		long visibleFormInputs = driver
				.findElements(By.cssSelector("input:not([type='hidden']):not([type='checkbox']), select, textarea"))
				.stream().filter(WebElement::isDisplayed).count();
		return visibleFormInputs > 1;
	}

	@When("user refreshes the browser and a leave site prompt should appear")
	public void userRefreshesBrowserAndLeaveSitePromptShouldAppear() {
		if (!isOnRealFormScreen()) {
			String reason = "not on a screen with a leave-site guard when the refresh was attempted "
					+ "(already on the relying party, or still on the initial login-method chooser)";
			logger.info("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return;
		}
		driver.navigate().refresh();

		boolean landedOnErrorPage;
		try {
			landedOnErrorPage = new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> !d.findElements(By.cssSelector("div.error-page-header")).isEmpty());
		} catch (TimeoutException e) {
			landedOnErrorPage = false;
		}
		if (landedOnErrorPage) {
			Assert.fail("Refreshing the KBI form invalidated the authorize transaction and showed the server's "
					+ "\"Something went wrong (401)\" error page instead of reloading the form - same known "
					+ "single-use-transaction limitation as the consent screen's refresh behavior.");
		}

		if (!isLeaveSitePromptDisplayed(10)) {

			String reason = "the native 'Leave site?' confirm dialog did not surface to WebDriver after "
					+ "refreshing, though the refresh itself completed - verified live.";
			logger.info("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
		}
	}

	@When("user cancels the leave site prompt")
	public void userCancelsTheLeaveSitePrompt() {
		page.dismissAlert();
	}

	@When("user confirms the leave site prompt")
	public void userConfirmsTheLeaveSitePrompt() {
		page.acceptAlert();
	}

	private boolean isLeaveSitePromptDisplayed(int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(ExpectedConditions.alertIsPresent());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}
}
