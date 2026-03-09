package stepdefinitions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.LoginOptionsPage;
import pages.SignUpPage;
import pages.SignupFormDynamicFiller;
import utils.ClaimsUtil;

public class LoginOptionsStepDefinition {

	public WebDriver driver;
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
		loginOptionsPage.setAuthorizeUrl(currentUrl);
	}

	@Then("verify dropdown language selection is present")
	public void verifyLanguageDropdown() {
		assertTrue(loginOptionsPage.isLanguageDropdownDisplayed());
	}

	@Then("verify multiple options for login is available")
	public void verifyMultipleLoginOptions() {
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		Assert.assertTrue("Expected multiple login options, but found: " + authFactors.size(), authFactors.size() > 1);
	}

	@Then("verify more ways to signIn option is available")
	public void verifyMoreWaysToSignInOption() {
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		boolean isMoreOptionsDisplayed = loginOptionsPage.isMoreWaysToSignInOptionDisplayed();

		if (authFactors.size() > 4) {
			assertTrue(isMoreOptionsDisplayed);
		} else {
			assertFalse(isMoreOptionsDisplayed);
		}
	}

	@When("user selects {string} from the language dropdown")
	public void userSelectsLanguage(String language) {
		loginOptionsPage.clickOnLanguageDropdown();
		loginOptionsPage.selectLanguage(language);
	}

	@Then("verify the UI is displayed in {string} language")
	public void verifyUILanguage(String language) {
		assertTrue(loginOptionsPage.isUILanguageChanged(language));
	}

	@Then("authentication screen should show login options based on acr_values from url")
	public void authenticationScreenShouldShowLoginOptionsBasedOnAuthFactorsFromUrl() throws Exception {
		ClaimsUtil.parseFromUrl(authorizeUrl);
		List<String> authFactors = ClaimsUtil.getAuthFactors();
		Map<String, WebElement> factorMap = loginOptionsPage.getAcrToElementMap();

		for (String factor : authFactors) {
			WebElement element = factorMap.get(factor);
			if (element != null) {
				assertTrue("Option not visible for " + factor, element.isDisplayed());
			}
		}
	}
}