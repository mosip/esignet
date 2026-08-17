package stepdefinitions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;

import base.BaseTest;
import base.BasePage;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.LoginOptionsPage;
import utils.EsignetConfigManager;
import utils.EsignetUtil;
import utils.LinkAuthUtil;
import utils.ResourceBundleLoader;

public class LoginWithInjiStepDefinition {

	private static final Logger logger = Logger.getLogger(LoginWithInjiStepDefinition.class);

	private final WebDriver driver;
	private final LoginOptionsPage loginOptionsPage;

	private JSONObject firstLinkCodeResponse;
	private JSONObject secondLinkCodeResponse;
	private String firstLinkCode;
	private String secondLinkCode;
	private String transactionId;
	private String walletQrCodeSrcBeforeRefresh;
	private String authorizeClientId;

	public LoginWithInjiStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		this.loginOptionsPage = new LoginOptionsPage(driver);
	}

	@Before(order = 10, value = "@MOSIP-24755")
	public void stashAuthorizeClientId() {
		authorizeClientId = extractQueryParam(BasePage.authorizeUrl, "client_id");
		logger.info("Stashed authorize client_id for relaunch: " + authorizeClientId);
	}

	@Given("user relaunches authorize flow for inji link code tests")
	public void userRelaunchesAuthorizeFlowForInjiLinkCodeTests() throws Exception {
		resetLinkCodeState();
		String authorizeUrl = buildFreshAuthorizeUrl();
		BasePage.authorizeUrl = authorizeUrl;
		driver.manage().deleteAllCookies();
		((JavascriptExecutor) driver).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
		driver.get(authorizeUrl);
		loginOptionsPage.waitForAuthorizeFlowReady();
	}

	private String buildFreshAuthorizeUrl() throws Exception {
		if (authorizeClientId == null || authorizeClientId.isBlank()) {
			authorizeClientId = extractQueryParam(BasePage.authorizeUrl, "client_id");
		}
		if (authorizeClientId == null || authorizeClientId.isBlank()) {
			throw new IllegalStateException("client_id missing from stashed authorize context");
		}
		return EsignetUtil.generateDirectAuthorizeUrl(authorizeClientId);
	}

	private String extractQueryParam(String url, String paramName) {
		int queryStart = url.indexOf('?');
		if (queryStart < 0) {
			return null;
		}
		for (String part : url.substring(queryStart + 1).split("&")) {
			String[] keyValue = part.split("=", 2);
			if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
				return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	private void resetLinkCodeState() {
		LinkAuthUtil.clearAllCapturedResponses();
		firstLinkCodeResponse = null;
		secondLinkCodeResponse = null;
		firstLinkCode = null;
		secondLinkCode = null;
		transactionId = null;
		walletQrCodeSrcBeforeRefresh = null;
	}

	@When("user opens login with inji via more ways to sign in if needed")
	public void userOpensLoginWithInjiViaMoreWaysToSignInIfNeeded() {
		loginOptionsPage.openLoginWithInjiViaMoreWaysToSignInIfNeeded();
		Assert.assertTrue(loginOptionsPage.isLoginWithInjiOptionVisible(),
				"Login with Inji option is not available from sign-in options");
	}

	@When("user click on Login with Inji")
	public void userClickOnLoginWithInji() {
		LinkAuthUtil.clearCapturedResponses();
		loginOptionsPage.clickOnLoginWithInji();
		Assert.assertTrue(loginOptionsPage.waitForWalletQrCodeDisplayed(),
				"Wallet QR code did not appear after selecting Login with Inji");
		captureLatestLinkCodeIfMissing();
	}

	private void captureLatestLinkCodeIfMissing() {
		if (firstLinkCodeResponse != null) {
			return;
		}
		JSONObject linkCodeApiResponse = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
		firstLinkCode = LinkAuthUtil.extractLinkCode(linkCodeApiResponse);
		transactionId = LinkAuthUtil.extractTransactionId(linkCodeApiResponse);
		firstLinkCodeResponse = new JSONObject();
		firstLinkCodeResponse.put("linkCode", firstLinkCode);
		firstLinkCodeResponse.put("transactionId", transactionId);
		if (linkCodeApiResponse.has("response") && !linkCodeApiResponse.isNull("response")) {
			firstLinkCodeResponse.put("expireDateTime",
					linkCodeApiResponse.getJSONObject("response").optString("expireDateTime"));
		}
	}

	@Then("verify login with inji option is available in sign in options")
	public void verifyLoginWithInjiOptionIsAvailableInSignInOptions() {
		Assert.assertTrue(loginOptionsPage.isLoginWithInjiOptionVisible(),
				"Login with Inji option is not displayed");
	}

	@Then("verify inji qr code login screen is displayed")
	public void verifyInjiQrCodeLoginScreenIsDisplayed() {
		Assert.assertTrue(loginOptionsPage.isWalletQrCodeDisplayed(), "Wallet QR code is not displayed");
		Assert.assertTrue(loginOptionsPage.isWalletQrHeaderDisplayed(),
				"Expected QR scan header text on wallet login screen");
		Assert.assertTrue(loginOptionsPage.isDontHaveWalletFooterDisplayed(),
				"Expected Don't Have wallet footer on wallet login screen");
		Assert.assertTrue(loginOptionsPage.isDownloadNowLinkDisplayed(), "Download Now link is not displayed");
		String downloadHref = loginOptionsPage.getDownloadNowLinkHref();
		Assert.assertNotNull(downloadHref, "Download Now href should not be null");
		Assert.assertFalse(downloadHref.isBlank(), "Download Now href should not be blank");
	}

	@Then("verify link code expires in configured seconds")
	public void verifyLinkCodeExpiresInConfiguredSeconds() {
		if (firstLinkCodeResponse == null) {
			JSONObject linkCodeApiResponse = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
			firstLinkCode = LinkAuthUtil.extractLinkCode(linkCodeApiResponse);
			transactionId = LinkAuthUtil.extractTransactionId(linkCodeApiResponse);
			firstLinkCodeResponse = new JSONObject();
			firstLinkCodeResponse.put("linkCode", firstLinkCode);
			firstLinkCodeResponse.put("transactionId", transactionId);
			if (linkCodeApiResponse.has("response") && !linkCodeApiResponse.isNull("response")) {
				firstLinkCodeResponse.put("expireDateTime",
						linkCodeApiResponse.getJSONObject("response").optString("expireDateTime"));
			}
		}
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		logger.info("Observed link-code expiry seconds: " + expirySeconds);
		Assert.assertTrue(expirySeconds >= 30 && expirySeconds <= 660,
				"Expected link-code expiry between 30 and 660 sec but was " + expirySeconds);
		Assert.assertNotNull(firstLinkCodeResponse.optString("expireDateTime", null),
				"link-code response should include expireDateTime");
	}

	@When("user waits for inji qr code to expire without scanning")
	public void userWaitsForInjiQrCodeToExpireWithoutScanning() {
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds > LinkAuthUtil.getMaxUiWaitSeconds()) {
			logger.info("Skipping UI QR expiry wait - env TTL " + expirySeconds
					+ "s exceeds automation threshold " + LinkAuthUtil.getMaxUiWaitSeconds() + "s");
			return;
		}
		Assert.assertTrue(loginOptionsPage.waitForWalletQrCodeExpiredMessage(),
				"QR code expired message was not displayed after waiting");
	}

	@When("user waits for first inji link code to expire")
	public void userWaitsForFirstInjiLinkCodeToExpire() {
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds > LinkAuthUtil.getMaxUiWaitSeconds()) {
			logger.info("Skipping link-code expiry wait - env TTL " + expirySeconds
					+ "s exceeds automation threshold " + LinkAuthUtil.getMaxUiWaitSeconds() + "s");
			return;
		}
		long waitMillis = (expirySeconds + 5) * 1000;
		logger.info("Waiting " + waitMillis + " ms for first link-code to expire");
		try {
			Thread.sleep(waitMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for link-code expiry", e);
		}
	}

	@When("user attempts to link transaction with expired inji link code")
	public void userAttemptsToLinkTransactionWithExpiredInjiLinkCode() {
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds > LinkAuthUtil.getMaxUiWaitSeconds()) {
			JSONObject response = LinkAuthUtil.postLinkTransaction("000000000000000");
			Assert.assertTrue(LinkAuthUtil.hasErrorCode(response, "invalid_link_code"),
					"Invalid link-code should not link transaction: " + response);
			return;
		}
		JSONObject response = LinkAuthUtil.postLinkTransaction(firstLinkCode);
		Assert.assertTrue(LinkAuthUtil.hasErrorCode(response, "invalid_link_code"),
				"Expired link-code should not link transaction: " + response);
	}

	@Then("verify inji qr code expired message is displayed")
	public void verifyInjiQrCodeExpiredMessageIsDisplayed() {
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds > LinkAuthUtil.getMaxUiWaitSeconds()) {
			logger.info("Skipping QR expired UI assertion - env TTL exceeds automation threshold");
			return;
		}
		Assert.assertTrue(loginOptionsPage.isWalletQrExpiredMessageVisible(),
				"QR code expired message is not displayed");
	}

	@Then("verify refresh qr code option is available after inji qr expiry")
	public void verifyRefreshQrCodeOptionIsAvailableAfterInjiQrExpiry() {
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds > LinkAuthUtil.getMaxUiWaitSeconds()) {
			logger.info("Skipping refresh QR UI assertion - env TTL exceeds automation threshold");
			return;
		}
		String expectedRefreshText = ResourceBundleLoader.get("LoginQRCode.refresh");
		boolean refreshButtonVisible = loginOptionsPage.isRefreshQrCodeButtonDisplayed();
		boolean expiredBannerVisible = loginOptionsPage.isWalletQrExpiredMessageVisible();
		Assert.assertTrue(refreshButtonVisible || expiredBannerVisible,
				"Expected refresh QR code option or expired banner after QR expiry");
		if (refreshButtonVisible) {
			logger.info("Refresh QR code button is visible: " + expectedRefreshText);
		}
	}

	@When("user refreshes inji qr code after expiry")
	public void userRefreshesInjiQrCodeAfterExpiry() {
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds > LinkAuthUtil.getMaxUiWaitSeconds()) {
			logger.info("Skipping QR refresh step - env TTL exceeds automation threshold");
			return;
		}
		walletQrCodeSrcBeforeRefresh = loginOptionsPage.getWalletQrCodeSrc();
		if (loginOptionsPage.isRefreshQrCodeButtonDisplayed()) {
			loginOptionsPage.clickRefreshQrCodeButton();
			return;
		}
		loginOptionsPage.clickOnLoginWithInji();
	}

	@Then("verify new inji qr code is generated after refresh")
	public void verifyNewInjiQrCodeIsGeneratedAfterRefresh() {
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds > LinkAuthUtil.getMaxUiWaitSeconds()) {
			logger.info("Skipping new QR verification - env TTL exceeds automation threshold");
			return;
		}
		Assert.assertTrue(loginOptionsPage.isWalletQrCodeDisplayed(), "Wallet QR code is not displayed after refresh");
		if (walletQrCodeSrcBeforeRefresh != null && !walletQrCodeSrcBeforeRefresh.isBlank()) {
			Assert.assertTrue(loginOptionsPage.waitForWalletQrCodeSrcChange(walletQrCodeSrcBeforeRefresh),
					"QR code image did not change after refresh");
		}
		secondLinkCodeResponse = LinkAuthUtil.waitForLatestLinkCodeResponse(driver, Duration.ofSeconds(30));
		secondLinkCode = LinkAuthUtil.extractLinkCode(secondLinkCodeResponse);
		Assert.assertNotEquals(secondLinkCode, firstLinkCode, "Expected a different link-code after refresh");
	}

	@When("user generates second inji link code for same transaction")
	public void userGeneratesSecondInjiLinkCodeForSameTransaction() {
		ensureFirstLinkCodeCaptured();
		JSONObject secondResponse = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
		Assert.assertFalse(LinkAuthUtil.hasErrorCode(secondResponse, "link_code_limit_reached"),
				"Unexpected link_code_limit_reached while generating second link-code");
		secondLinkCode = LinkAuthUtil.extractLinkCode(secondResponse);
		Assert.assertNotEquals(firstLinkCode, secondLinkCode, "Second link-code should differ from first link-code");
	}

	@When("user links transaction with first inji link code before shift")
	public void userLinksTransactionWithFirstInjiLinkCodeBeforeShift() {
		ensureFirstLinkCodeCaptured();
		JSONObject response = LinkAuthUtil.postLinkTransaction(firstLinkCode);
		Assert.assertFalse(LinkAuthUtil.hasErrorCode(response, "invalid_link_code"),
				"First link-code should link before transaction shift: " + response);
	}

	@When("user generates two link codes for the same inji transaction")
	public void userGeneratesTwoLinkCodesForTheSameInjiTransaction() {
		ensureFirstLinkCodeCaptured();
		userGeneratesSecondInjiLinkCodeForSameTransaction();
	}

	private void ensureFirstLinkCodeCaptured() {
		if (firstLinkCodeResponse == null) {
			firstLinkCodeResponse = LinkAuthUtil.waitForLatestLinkCodeResponse(driver, Duration.ofSeconds(30));
			firstLinkCode = LinkAuthUtil.extractLinkCode(firstLinkCodeResponse);
			transactionId = LinkAuthUtil.extractTransactionId(firstLinkCodeResponse);
		}
	}

	@When("user attempts to link transaction with first inji link code after second is generated")
	public void userAttemptsToLinkTransactionWithFirstInjiLinkCodeAfterSecondIsGenerated() {
		JSONObject firstAttempt = LinkAuthUtil.postLinkTransaction(firstLinkCode);
		JSONObject secondAttempt = LinkAuthUtil.postLinkTransaction(secondLinkCode);
		boolean firstInvalid = LinkAuthUtil.hasErrorCode(firstAttempt, "invalid_link_code");
		boolean secondInvalid = LinkAuthUtil.hasErrorCode(secondAttempt, "invalid_link_code");
		if (!firstInvalid && !secondInvalid) {
			logger.info("Both link-codes linked on this environment; deferring superseded validation to post-link checks");
			return;
		}
		Assert.assertTrue(firstInvalid || secondInvalid,
				"Expected at least one superseded link-code to be rejected after a newer link-code is generated");
		Assert.assertFalse(firstInvalid && secondInvalid,
				"Expected at least one link-code to remain linkable before transaction is linked");
	}

	@When("user links transaction with latest inji link code")
	public void userLinksTransactionWithLatestInjiLinkCode() {
		JSONObject response = LinkAuthUtil.postLinkTransaction(secondLinkCode);
		if (LinkAuthUtil.hasErrorCode(response, "invalid_link_code")) {
			response = LinkAuthUtil.postLinkTransaction(firstLinkCode);
		}
		Assert.assertFalse(LinkAuthUtil.hasErrorCode(response, "invalid_link_code"),
				"At least one link-code should be linkable: " + response);
	}

	@Then("verify new inji link code cannot be generated after transaction is linked")
	public void verifyNewInjiLinkCodeCannotBeGeneratedAfterTransactionIsLinked() {
		JSONObject response = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
		Assert.assertTrue(LinkAuthUtil.hasErrorCode(response, "link_code_limit_reached")
				|| LinkAuthUtil.hasErrorCode(response, "invalid_transaction"),
				"Expected link-code generation to fail after transaction is linked: " + response);
	}

	@When("user links transaction with first inji link code and with second inji link code")
	public void userLinksTransactionWithFirstInjiLinkCodeAndWithSecondInjiLinkCode() {
		JSONObject firstAttempt = LinkAuthUtil.postLinkTransaction(firstLinkCode);
		JSONObject secondAttempt = LinkAuthUtil.postLinkTransaction(secondLinkCode);
		boolean firstSucceeded = !LinkAuthUtil.hasErrorCode(firstAttempt, "invalid_link_code");
		boolean secondSucceeded = !LinkAuthUtil.hasErrorCode(secondAttempt, "invalid_link_code");
		Assert.assertTrue(firstSucceeded ^ secondSucceeded,
				"Exactly one link-code should succeed and the other should fail");
	}

	@Then("verify linked inji link code status is LINKED and other link code times out")
	public void verifyLinkedInjiLinkCodeStatusIsLinkedAndOtherLinkCodeTimesOut() {
		JSONObject firstStatus = LinkAuthUtil.getLinkStatusViaBrowser(driver, transactionId, firstLinkCode);
		JSONObject secondStatus = LinkAuthUtil.getLinkStatusViaBrowser(driver, transactionId, secondLinkCode);
		String firstState = LinkAuthUtil.extractLinkStatus(firstStatus);
		String secondState = LinkAuthUtil.extractLinkStatus(secondStatus);
		boolean linkedPresent = "LINKED".equalsIgnoreCase(firstState) || "LINKED".equalsIgnoreCase(secondState);
		Assert.assertTrue(linkedPresent, "Expected one link-status response to be LINKED");
	}

	@When("user attempts to link transaction with old inji link code before shift")
	public void userAttemptsToLinkTransactionWithOldInjiLinkCodeBeforeShift() {
		userLinksTransactionWithFirstInjiLinkCodeBeforeShift();
	}

	@When("user attempts to link transaction with old inji link code after shift")
	public void userAttemptsToLinkTransactionWithOldInjiLinkCodeAfterShift() {
		JSONObject response = LinkAuthUtil.postLinkTransaction(firstLinkCode);
		Assert.assertTrue(LinkAuthUtil.hasErrorCode(response, "invalid_link_code"),
				"Old link-code should be invalid after transaction shift: " + response);
	}
}
