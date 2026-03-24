package stepdefinitions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.testng.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.ConsentPage;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.ClaimsUtil;

public class LoginOptionsStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(LoginOptionsStepDefinition.class);
	LoginOptionsPage loginOptionsPage;
	SignUpPage signUpPage;
	SignupFormDynamicFiller formFiller;

	public LoginOptionsStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		loginOptionsPage = new LoginOptionsPage(driver);
		signUpPage = new SignUpPage(driver);
		formFiller = new SignupFormDynamicFiller(driver);

	}

	private String authorizeUrl;

	@Given("user captures the authorize url")
	public void userCapturesAuhtorizeUrl() throws Exception {
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.urlContains("#"));
		String currentUrl = driver.getCurrentUrl();
		this.authorizeUrl = currentUrl;
		loginOptionsPage.setAuthorizeUrl(currentUrl);
	}

	@Then("verify dropdown language selection is present")
	public void verifyLanguageDropdown() {
		Assert.assertTrue(loginOptionsPage.isLanguageDropdownDisplayed(),
				"Language dropdown is not displayed on the esignet page");
	}

	@Then("verify multiple options for login is available")
	public void verifyMultipleLoginOptions() {
		ClaimsUtil.parseFromUrl(authorizeUrl);
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		Assert.assertTrue(authFactors.size() > 1, "Expected multiple login options, but found: " + authFactors.size());
	}

	@Then("verify more ways to signIn option is available")
	public void verifyMoreWaysToSignInOption() {
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		Assert.assertFalse(authFactors.isEmpty(), "No auth factors were parsed from the authorize URL");
		boolean isMoreOptionsDisplayed = loginOptionsPage.isMoreWaysToSignInOptionDisplayed();

		if (authFactors.size() > 4) {
			Assert.assertTrue(isMoreOptionsDisplayed, "Multiple options were not displayed");
		} else {
			assertFalse(isMoreOptionsDisplayed);
		}
	}

	@When("user selects {string} from the language dropdown")
	public void userSelectsLanguage(String language) {
		loginOptionsPage.clickOnLanguageDropdown();
		loginOptionsPage.selectLanguage(language);
	}

	@Then("verify the UI is displayed in selected language {string}")
	public void verifyUILanguage(String text) {
		Assert.assertTrue(loginOptionsPage.isUILanguageChanged(text),
				"UI language did not change to expected language");
	}

	@Then("authentication screen should show login options based on acr_values from url")
	public void authenticationScreenShouldShowLoginOptionsBasedOnAuthFactorsFromUrl() throws Exception {
		ClaimsUtil.parseFromUrl(authorizeUrl);
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		Map<String, WebElement> factorMap = loginOptionsPage.getAcrToElementMap();

		for (String factor : authFactors) {
			String normalizedFactor = ClaimsUtil.normalizeFactor(factor);
			WebElement element = factorMap.get(normalizedFactor);
			assertNotNull("No UI mapping found for factor: " + factor, element);
			Assert.assertTrue(element.isDisplayed(), "Option not visible for " + factor);
		}
	}

	@When("user triggers the authorization endpoint, the response should have status code 200 and contain valid HTML with JS content")
	public void triggerAuthorizationEndpoint() throws IOException {

	    // Get base URL without fragment (# part)
	    String currentUrl = driver.getCurrentUrl();
	    String baseUrl = currentUrl.split("#")[0];

	    URI uri = URI.create(baseUrl);
	    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
	    connection.setRequestMethod("GET");

	    int statusCode = connection.getResponseCode();
	    Assert.assertEquals(statusCode, 200, "Expected status code 200");

	    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	    StringBuilder response = new StringBuilder();
	    String line;

	    while ((line = reader.readLine()) != null) {
	        response.append(line);
	    }
	    reader.close();

	    String html = response.toString();

	    Assert.assertTrue(html.contains("<html"), "HTML tag not found");
	    Assert.assertTrue(html.contains("<script"), "Script tag not found");
	}
	
	@Then("user verifies the behavior after resizing the browser window to different dimensions")
	public void userResizesBrowserWindowToDifferentDimensions() {
		int[][] screenSizes = { { 1920, 1080 }, { 1366, 768 }, { 768, 1024 }, { 414, 896 } };

		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

		for (int[] size : screenSizes) {
			driver.manage().window().setSize(new Dimension(size[0], size[1]));
			wait.until(ExpectedConditions.visibilityOf(loginOptionsPage.getLoginWithOtpButton()));
			logger.info("Resized to: " + size[0] + "x" + size[1]);
		}
	}

	@Then("user verify the otp button remain visible and aligned after resizing")
	public void verifyOtpButtonResponsiveness() {
		WebElement esignetButton = loginOptionsPage.getLoginWithOtpButton();
		Assert.assertTrue(esignetButton.isDisplayed(), "eSignet button not visible after resizing");
		Assert.assertTrue(esignetButton.getRect().getWidth() > 0, "eSignet button width collapsed");
		Assert.assertTrue(esignetButton.getRect().getX() >= 0, "eSignet button misaligned or offscreen");
	}

	@Then("user views the portal on multiple screen sizes")
	public void userViewsPortalOnDifferentScreenSizes() {
		int[][] mobileSizes = { { 360, 640 }, { 390, 844 }, { 412, 915 } };

		for (int[] size : mobileSizes) {
			driver.manage().window().setSize(new Dimension(size[0], size[1]));
			logger.info("Testing layout at resolution: " + size[0] + "x" + size[1]);
		}
	}

}