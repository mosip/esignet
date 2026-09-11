package stepdefinitions;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.BrowserCompatibilityPage;

public class BrowserCompatibilityStepDefinition {

	public WebDriver driver;
	BrowserCompatibilityPage browserCompatibilityPage;

	public BrowserCompatibilityStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		browserCompatibilityPage = new BrowserCompatibilityPage(driver);
	}

	private static final String UNSUPPORTED_USER_AGENT = "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.1; Trident/4.0)";

	@When("user's browser is overridden to an unsupported user agent")
	public void userBrowserIsOverriddenToUnsupportedUserAgent() {
		browserCompatibilityPage.setUserAgentOverride(UNSUPPORTED_USER_AGENT);
		pages.ConsentPage.refreshAfterAttentionProceed = true;
	}

	@Then("verify the browser compatibility screen header is displayed as {string}")
	public void verifyBrowserCompatibilityScreenHeader(String expectedHeader) {
		Assert.assertTrue(browserCompatibilityPage.isHeaderDisplayed(),
				"Header '" + expectedHeader + "' is not displayed on the browser compatibility screen");
	}

	@Then("verify the browser compatibility screen subheader is displayed")
	public void verifyBrowserCompatibilityScreenSubHeader() {
		Assert.assertTrue(browserCompatibilityPage.isSubHeaderDisplayed(),
				"Subheader is not displayed on the browser compatibility screen");
	}

	@Then("verify the browser compatibility screen subheader message is {string}")
	public void verifyBrowserCompatibilityScreenSubHeaderMessage(String expectedMessage) {
		Assert.assertEquals(browserCompatibilityPage.getSubHeaderText(), expectedMessage,
				"Browser compatibility screen subheader message did not match the expected text");
	}

	@Then("verify the Okay button is displayed on the browser compatibility screen")
	public void verifyOkayButtonDisplayedOnBrowserCompatibilityScreen() {
		Assert.assertTrue(browserCompatibilityPage.isOkayButtonDisplayed(),
				"Okay button is not displayed on the browser compatibility screen");
	}

	@Then("verify the Okay button is clickable on the browser compatibility screen")
	public void verifyOkayButtonIsClickableOnBrowserCompatibilityScreen() {
		Assert.assertTrue(browserCompatibilityPage.isOkayButtonEnabled(),
				"Okay button is not enabled/clickable on the browser compatibility screen");
	}

	@When("user clicks on the Okay button on the browser compatibility screen")
	public void userClicksOnOkayButtonOnBrowserCompatibilityScreen() {
		browserCompatibilityPage.clickOnOkayButton();
	}

	@Then("verify user is redirected to the relying party with the incompatible browser error")
	public void verifyUserIsRedirectedToRelyingPartyWithIncompatibleBrowserError() {
		Assert.assertTrue(browserCompatibilityPage.waitForIncompatibleBrowserRedirect(),
				"User was not redirected to the relying party with the incompatible_browser error and expected message");
	}

	@Then("verify the logo is displayed on the browser compatibility screen")
	public void verifyLogoDisplayedOnBrowserCompatibilityScreen() {
		Assert.assertTrue(browserCompatibilityPage.isLogoDisplayed(),
				"Logo is not displayed on the browser compatibility screen");
	}

	@Then("verify the language dropdown is displayed on the browser compatibility screen")
	public void verifyLanguageDropdownDisplayedOnBrowserCompatibilityScreen() {
		Assert.assertTrue(browserCompatibilityPage.isLanguageDropdownDisplayed(),
				"Language dropdown is not displayed on the browser compatibility screen");
	}

	@Then("verify the powered by eSignet footer is displayed on the browser compatibility screen")
	public void verifyPoweredByFooterDisplayedOnBrowserCompatibilityScreen() {
		Assert.assertTrue(browserCompatibilityPage.isPoweredByFooterDisplayed(),
				"'Powered by eSignet' footer is not displayed on the browser compatibility screen");
	}
}
