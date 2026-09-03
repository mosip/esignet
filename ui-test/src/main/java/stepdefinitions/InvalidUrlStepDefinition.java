package stepdefinitions;

import org.testng.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.log4j.Logger;
import org.openqa.selenium.WebDriver;

import base.BasePage;
import base.BaseTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.SecurityXSSException;
import pages.InvalidUrlPage;
import pages.LoginOptionsPage;
import utils.EsignetConfigManager;
import utils.EsignetUtil;

public class InvalidUrlStepDefinition extends AdminTestUtil {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(InvalidUrlStepDefinition.class);
	InvalidUrlPage invalidUrlPage;
	LoginOptionsPage loginOptionsPage;

	public InvalidUrlStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		invalidUrlPage = new InvalidUrlPage(driver);
		loginOptionsPage = new LoginOptionsPage(driver);

	}

	private boolean lastUrlMutationApplicable = true;
	private String lastUrlMutationReason = "";

	private void checkSegmentPresent(String url, String segment, String stepDescription) {
		lastUrlMutationApplicable = url != null && url.contains(segment);
		if (!lastUrlMutationApplicable) {
			lastUrlMutationReason = "esignet-go's URL never contains \"" + segment + "\" (no path-based "
					+ "routing for this screen) - \"" + stepDescription + "\" has nothing to tamper with here.";
			logger.info("Not tampering (this step only, not the scenario) - " + lastUrlMutationReason);
			utils.ExtentReportManager.notApplicable(lastUrlMutationReason);
		}
	}

	private void checkSignupServiceApplicable(String stepDescription) {
		lastUrlMutationApplicable = EsignetUtil.isSignupServiceDeployed();
		if (!lastUrlMutationApplicable) {
			lastUrlMutationReason = "signup service is not deployed in this environment - \"" + stepDescription
					+ "\" has nothing real to tamper with.";
			logger.info("Not tampering (this step only, not the scenario) - " + lastUrlMutationReason);
			utils.ExtentReportManager.notApplicable(lastUrlMutationReason);
		}
	}

	@When("user modifies domain in the esignet url")
	public void userModifiesDomainInUrl() {
		if (BasePage.authorizeUrl == null) {
			throw new IllegalStateException("authorizeUrl is not set");
		}
		BasePage.authorizeUrlTampered = true;
		String invalidUrl = BasePage.authorizeUrl.replaceFirst("://[^/]+", "://invalid.mosip.net");
		try {
			driver.get(invalidUrl);
		} catch (Exception e) {
			Assert.assertTrue(e.getMessage().contains("ERR_NAME_NOT_RESOLVED"));
		}
	}

	@Then("verify this site can’t be reached error is displayed")
	public void verifySiteCantBeReachedErrorDisplayed() {
		if (!lastUrlMutationApplicable) {
			logger.info("Not checking (this step only, not the scenario) - " + lastUrlMutationReason);
			utils.ExtentReportManager.notApplicable(lastUrlMutationReason);
			return;
		}
		String pageSource = driver.getPageSource();
		Assert.assertTrue(pageSource.contains("ERR_NAME_NOT_RESOLVED"), "Expected error not displayed");
	}

	@When("user modify the hash value in the esignet url")
	public void userManipulatesHashValue() {
		BasePage.authorizeUrlTampered = true;
		String currentUrl = driver.getCurrentUrl();
		String modifiedUrl = currentUrl.replace("#", "#invalid");
		driver.get(modifiedUrl);
	}

	@Then("verify unable to process Please try again error is displayed")
	public void verifyUnableToProcessErrorDisplayed() {
		Assert.assertTrue(invalidUrlPage.isUnableToProcessErrorDisplayed(), "Error message is not displayed");
	}

	private boolean errorPageLanguageDropdownApplicable = true;

	@When("user change the language to {string} from dropdown")
	public void userChangeLanguage(String language) {
		errorPageLanguageDropdownApplicable = !driver.findElements(org.openqa.selenium.By.id("language_dropdown")).isEmpty();
		if (!errorPageLanguageDropdownApplicable) {
			String reason = "this error page has no language dropdown - verified live.";
			logger.info("Not switching language (this step only, not the scenario) - " + reason);
			utils.ExtentReportManager.notApplicable(reason);
			return;
		}
		invalidUrlPage.clickOnLanguageDropdownOption();
		loginOptionsPage.selectLanguage(language);
	}

	@Then("verify {string} message is displayed in chosen language")
	public void verifyErrorMessageChangedAsPerLanguage(String text) {
		if (!errorPageLanguageDropdownApplicable) {
			String reason = "no language switch happened, so no changed-language message is expected.";
			logger.info("Not checking - " + reason);
			utils.ExtentReportManager.notApplicable(reason);
			return;
		}
		Assert.assertTrue(invalidUrlPage.isErrorMsgLanguageChanged(text),
				"Error message language did not change to expected language");
	}

	@When("user modify the nonce value in esignet url")
	public void userModifyNonceValue() {
		if (BasePage.authorizeUrl == null) {
			throw new IllegalStateException("authorizeUrl is not set");
		}
		BasePage.authorizeUrlTampered = true;

		String modifiedUrl = BasePage.authorizeUrl.replaceFirst("nonce=[^&]*", "nonce=9999999999999");
		driver.get(modifiedUrl);
	}

	@Then("verify user remain on same esignet page without any error")
	public void verifyUserRemainsOnEsignetPage() {
		Assert.assertTrue(invalidUrlPage.isEsignetPageRetained(), "Page doesn't retained");
	}

	@Then("verify unauthorized error is displayed for invalid nonce")
	public void verifyUnauthorizedErrorDisplayedForInvalidNonce() {
		Assert.assertTrue(invalidUrlPage.isUnauthorizedErrorDisplayed(),
				"Unauthorized/something-went-wrong error is not displayed for a tampered nonce - instead landed on "
						+ driver.getCurrentUrl());
	}

	@When("user remove the nonce and state value in esignet url")
	public void userRemoveNonceAndStateValue() throws JsonProcessingException, SecurityXSSException {
		BasePage.authorizeUrlTampered = true;
		String urlWithoutNonce = EsignetUtil.generateAuthorizeUrlWithoutNonceAndState();
		BasePage.authorizeUrl = urlWithoutNonce;
		driver.get(urlWithoutNonce);
	}

	@When("user modify the state value in esignet url")
	public void userModifyStateValue() throws JsonProcessingException, SecurityXSSException {
		BasePage.authorizeUrlTampered = true;
		String sourceUrl = authorizeUrlUsableForOidcTamper();
		String modifiedUrl = sourceUrl.contains("state=")
				? sourceUrl.replaceFirst("state=[^&]*", "state=invalidStateValue123")
				: sourceUrl + (sourceUrl.contains("?") ? "&" : "?") + "state=invalidStateValue123";
		driver.get(modifiedUrl);
	}

	private String authorizeUrlUsableForOidcTamper() throws SecurityXSSException, JsonProcessingException {
		String url = BasePage.authorizeUrl;
		if (url != null && url.contains("client_id=") && url.contains("code_challenge=")) {
			return url;
		}
		String clientId = EsignetUtil.resolveClientId("$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$");
		return EsignetUtil.generateDirectAuthorizeUrlWithPkce(clientId);
	}

	@When("user modify the login value in esignet url")
	public void userModifyLoginValue() {
		if (BasePage.authorizeUrl == null) {
			throw new IllegalStateException("authorizeUrl is not set");
		}
		BasePage.authorizeUrlTampered = true;
		checkSegmentPresent(BasePage.authorizeUrl, "/login", "modify the login value in esignet url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = BasePage.authorizeUrl.replace("/login", "/invalid");
		driver.get(modifiedUrl);
	}

	@When("user remove the login value in esignet url")
	public void userRemoveLoginValue() {
		if (BasePage.authorizeUrl == null) {
			throw new IllegalStateException("authorizeUrl is not set");
		}
		BasePage.authorizeUrlTampered = true;
		checkSegmentPresent(BasePage.authorizeUrl, "/login", "remove the login value in esignet url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = BasePage.authorizeUrl.replace("/login", " ");
		driver.get(modifiedUrl);
	}

	@Then("verify the page you are looking for does not exist error is displayed")
	public void verifyPageDoesNotExistErrorDisplayed() {
		if (!lastUrlMutationApplicable) {
			logger.info("Not checking - nothing was tampered with, so no error page is expected.");
			utils.ExtentReportManager.notApplicable("nothing was tampered with (" + lastUrlMutationReason
					+ ") - no error page is expected.");
			return;
		}
		Assert.assertTrue(invalidUrlPage.isPageDoesNotExistErrorMsgDisplayed(), "Error message is not displayed");
	}

	@When("user modifies authorize value in esignet url")
	public void userModifiesAuthorizeValue() throws Exception {
		BasePage.authorizeUrlTampered = true;
		lastUrlMutationApplicable = true;
		String url = authorizeUrlUsableForOidcTamper();
		String modifiedUrl = url.replace("/authorize", "/invalid");
		driver.get(modifiedUrl);
	}

	@When("user removes authorize value in esignet url")
	public void userRemovesAuthorizeInUrl() throws Exception {
		BasePage.authorizeUrlTampered = true;
		lastUrlMutationApplicable = true;
		String url = authorizeUrlUsableForOidcTamper();
		String modifiedUrl = url.contains("authorize?") ? url.replace("authorize?", "")
				: url.replace("/authorize", "/");
		driver.get(modifiedUrl);
	}

	@Given("user relaunches esignet url")
	public void userRelaunchesEsignetUrl() throws JsonProcessingException, SecurityXSSException {
		BasePage.authorizeUrlTampered = false;
		String clientId = EsignetUtil.resolveClientId("$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$");
		String url = EsignetUtil.generateDirectAuthorizeUrlWithPkce(clientId);
		BasePage.authorizeUrl = url;
		driver.get(url);
		BasePage.markAuthorizeSessionFresh();
	}

	@Then("verify user navigated to attention screen")
	public void verifyAttentionScreenDisplayed() {
		Assert.assertTrue(invalidUrlPage.isAttentionScreenDisplayed(), "Did not navigated to attention page");
	}

	@When("user modify the claims details value in esignet url")
	public void userModifyClaimsDetailsValue() {
		String currentUrl = driver.getCurrentUrl();
		checkSegmentPresent(currentUrl, "/claim-details", "modify the claims details value in esignet url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = currentUrl.replace("/claim-details", "/claim-detail");
		driver.get(modifiedUrl);
	}

	@When("user remove the claim details value in signup url")
	public void userRemoveClaimDetailsValue() {
		String currentUrl = driver.getCurrentUrl();
		checkSegmentPresent(currentUrl, "/claim-detail", "remove the claim details value in signup url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = currentUrl.replace("/claim-detail", " ");
		driver.get(modifiedUrl);
	}

	@When("user modify the identity verification value in esignet url")
	public void userModifyIdentitiyVerificationValue() {
		String currentUrl = driver.getCurrentUrl();
		checkSegmentPresent(currentUrl, "/identity-verification", "modify the identity verification value in esignet url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = currentUrl.replace("/identity-verification", "/invalid");
		driver.get(modifiedUrl);
	}

	@When("user remove the identity verification value in esignet url")
	public void userRemoveIdentitiyVerificationValue() {
		String currentUrl = driver.getCurrentUrl();
		checkSegmentPresent(currentUrl, "/identity-verification", "remove the identity verification value in esignet url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = currentUrl.replace("/identity-verification", " ");
		driver.get(modifiedUrl);
	}

	@When("user modify the consent value in esignet url")
	public void userModifyConsentValue() {
		String currentUrl = driver.getCurrentUrl();
		checkSegmentPresent(currentUrl, "/consent", "modify the consent value in esignet url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = currentUrl.replace("/consent", "/invalid");
		driver.get(modifiedUrl);
	}

	@When("user remove the consent value in esignet url")
	public void userRemoveConsentValue() {
		String currentUrl = driver.getCurrentUrl();
		checkSegmentPresent(currentUrl, "/consent", "remove the consent value in esignet url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String modifiedUrl = currentUrl.replace("/consent", " ");
		driver.get(modifiedUrl);
	}

	@When("user modifies domain in the signup url")
	public void userModifiesDomainInSignupUrl() {
		checkSignupServiceApplicable("modify domain in the signup url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String currentUrl = driver.getCurrentUrl();
		String invalidUrl = currentUrl.replaceFirst("://[^/]+", "://invalid.mosip.net");
		try {
			driver.get(invalidUrl);
		} catch (Exception e) {
			Assert.assertTrue(e.getMessage().contains("ERR_NAME_NOT_RESOLVED"));
		}
	}

	@When("user modifies the signup value in signup url")
	public void userModifiesSignupValue() {
		checkSignupServiceApplicable("modify the signup value in signup url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String currentUrl = driver.getCurrentUrl();
		int lastIndex = currentUrl.lastIndexOf("/signup");
		if (lastIndex < 0) {
			String reason = "current URL has no '/signup' segment: " + currentUrl;
			logger.info("Not tampering (this step only, not the scenario) - " + reason);
			utils.ExtentReportManager.notApplicable(reason);
			return;
		}
		String modifiedUrl = currentUrl.substring(0, lastIndex) + "/invalid"
				+ currentUrl.substring(lastIndex + "/signup".length());
		driver.get(modifiedUrl);
	}

	@Then("verify error screen along with reset password button and register button is displayed")
	public void userVerifyErrorScreen() {
		if (!lastUrlMutationApplicable) {
			logger.info("Not checking - nothing was tampered with, so no error page is expected.");
			utils.ExtentReportManager.notApplicable("nothing was tampered with (" + lastUrlMutationReason
					+ ") - no error page is expected.");
			return;
		}
		Assert.assertTrue(invalidUrlPage.isPageNotExistErrorScreenDisplayed(), "Error screen did not displayed");
		Assert.assertTrue(invalidUrlPage.isResetPasswordButtonVisible(), "Reset password button did not displayed");
		Assert.assertTrue(invalidUrlPage.isRegisterButtonVisible(), "Register button did not displayed");
	}

	@When("user remove the signup value in signup url")
	public void userRemoveSignupValue() {
		checkSignupServiceApplicable("remove the signup value in signup url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String currentUrl = driver.getCurrentUrl();
		int lastIndex = currentUrl.lastIndexOf("/signup");
		if (lastIndex < 0) {
			String reason = "current URL has no '/signup' segment: " + currentUrl;
			logger.info("Not tampering (this step only, not the scenario) - " + reason);
			utils.ExtentReportManager.notApplicable(reason);
			return;
		}
		String modifiedUrl = currentUrl.substring(0, lastIndex) + currentUrl.substring(lastIndex + "/signup".length());
		driver.get(modifiedUrl);
	}

	@Then("user click on reset password button")
	public void userClickOnResetPasswordButton() {
		if (!EsignetUtil.isSignupServiceDeployed()) {
			String reason = "signup service is not deployed in this environment - no reset password "
					+ "button to click.";
			logger.info("Not clicking (this step only, not the scenario) - " + reason);
			utils.ExtentReportManager.notApplicable(reason);
			return;
		}
		invalidUrlPage.clickOnResetPasswordButton();
	}

	@When("user modifies the reset password value in signup url")
	public void userModifiesResetPasswordValue() {
		checkSignupServiceApplicable("modify the reset password value in signup url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String currentUrl = driver.getCurrentUrl();
		String modifiedUrl = currentUrl.replace("reset-password", "invalid");
		driver.get(modifiedUrl);
	}

	@When("user remove the reset password value in signup url")
	public void userRemoveResetPasswordValue() {
		checkSignupServiceApplicable("remove the reset password value in signup url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String currentUrl = driver.getCurrentUrl();
		String modifiedUrl = currentUrl.replace("reset-password", "");
		driver.get(modifiedUrl);
	}

	@When("user navigates to something went wrong page")
	public void userNavigatesToErrorPage() {
		checkSignupServiceApplicable("navigate to something went wrong page");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String baseSignupUrl = EsignetConfigManager.getSignupUrl();
		String modifiedUrl = baseSignupUrl + "something-went-wrong";
		driver.get(modifiedUrl);
	}

	@Then("verify something went wrong our experts are working hard to make things working again error message displayed")
	public void userVerifySomethingWentWrongErrorScreen() {
		if (!lastUrlMutationApplicable) {
			logger.info("Not checking - nothing was tampered with, so no error page is expected.");
			utils.ExtentReportManager.notApplicable("nothing was tampered with (" + lastUrlMutationReason
					+ ") - no error page is expected.");
			return;
		}
		Assert.assertTrue(invalidUrlPage.isSomethingWentWrongErrorDisplayed(),
				"Something went wrong error screen did not displayed");
	}

	@When("user remove the something went wrong value in signup url")
	public void userRemoveSomethingWentWrongValue() {
		checkSignupServiceApplicable("remove the something went wrong value in signup url");
		if (!lastUrlMutationApplicable) {
			return;
		}
		String currentUrl = driver.getCurrentUrl();
		String modifiedUrl = currentUrl.replace("something-went-wrong", "");
		driver.get(modifiedUrl);
	}

}
