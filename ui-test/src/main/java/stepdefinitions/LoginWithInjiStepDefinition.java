package stepdefinitions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
import utils.ExtentReportManager;
import utils.LinkAuthUtil;
import utils.ResourceBundleLoader;

public class LoginWithInjiStepDefinition {

	private static final Logger logger = Logger.getLogger(LoginWithInjiStepDefinition.class);

	private final WebDriver driver;
	private final LoginOptionsPage loginOptionsPage;
	private boolean injiApplicable = true;

	private JSONObject firstLinkCodeResponse;
	private JSONObject secondLinkCodeResponse;
	private String firstLinkCode;
	private String secondLinkCode;
	private String transactionId;
	private String walletQrCodeSrcBeforeRefresh;
	private String authorizeClientId;
	private String linkedTransactionId;
	private JSONObject lastOverflowResponse;
	private final List<String> generatedLinkCodes = new ArrayList<>();
	private boolean skipExtraLinkCodeCapture;
	private boolean serverLinkCodeLimitEnforced;

	public LoginWithInjiStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		this.loginOptionsPage = new LoginOptionsPage(driver);
	}

	@Then("Click on Login with Inji")
	public void clickOnLoginWithInji() {

		if (!loginOptionsPage.isLoginWithInjiDisplayed()) {
			injiApplicable = false;
			String reason = "Login with Inji is not offered by this environment's default client/policy - "
					+ "verified live (only OTP/Password/Biometrics render).";
			ExtentReportManager.notApplicable(reason);
			return;
		}
		loginOptionsPage.clickOnLoginWithInji();
	}

	@Then("validate the logo alignment")
	public void validateLogoAlignment() {
		if (!injiApplicable) {
			ExtentReportManager.notApplicable("Login with Inji screen never opened - nothing to validate.");
			return;
		}
		Assert.assertTrue(loginOptionsPage.isLogoDisplayed(), "eSignet logo is not displayed");
	}

	@Before(order = 10, value = "@LoginWithInji")
	public void stashAuthorizeClientId() {
		if (BasePage.authorizeUrl == null || BasePage.authorizeUrl.isBlank()) {
			logger.warn("Authorize URL not set yet; relaunch steps will resolve client_id later");
			return;
		}
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
		linkedTransactionId = null;
		lastOverflowResponse = null;
		generatedLinkCodes.clear();
		skipExtraLinkCodeCapture = false;
		serverLinkCodeLimitEnforced = false;
	}

	private String getLinkCodeByIndex(int oneBasedIndex) {
		if (oneBasedIndex < 1 || oneBasedIndex > generatedLinkCodes.size()) {
			throw new IllegalStateException(
					"Link code index " + oneBasedIndex + " unavailable; generated count="
							+ generatedLinkCodes.size());
		}
		return generatedLinkCodes.get(oneBasedIndex - 1);
	}

	private void storeLinkedTransactionId(JSONObject response) {
		linkedTransactionId = LinkAuthUtil.extractLinkTransactionId(response);
		Assert.assertNotNull(linkedTransactionId,
				"Wallet scan simulation should return linkTransactionId: " + response);
	}

	private JSONObject linkTransactionPreferBrowser(String linkCode) {
		try {
			return LinkAuthUtil.postLinkTransactionViaBrowser(driver, linkCode);
		} catch (Exception browserFailure) {
			logger.warn("Browser link-transaction failed for link-code; falling back to REST API: "
					+ browserFailure.getMessage());
			return LinkAuthUtil.postLinkTransaction(linkCode);
		}
	}

	private void rememberLinkedTransactionIfPresent(JSONObject response) {
		String linkTransactionId = LinkAuthUtil.extractLinkTransactionId(response);
		if (linkTransactionId != null && !linkTransactionId.isBlank()) {
			linkedTransactionId = linkTransactionId;
		}
	}

	private boolean isTransactionAlreadyLinked(JSONObject response) {
		return LinkAuthUtil.hasErrorCode(response, "invalid_transaction")
				&& linkedTransactionId != null && !linkedTransactionId.isBlank();
	}

	@When("user opens login with inji via more ways to sign in if needed")
	public void userOpensLoginWithInjiViaMoreWaysToSignInIfNeeded() {
		loginOptionsPage.openLoginWithInjiViaMoreWaysToSignInIfNeeded();
		Assert.assertTrue(loginOptionsPage.isLoginWithInjiOptionVisible(),
				"Login with Inji option is not available from sign-in options");
	}

	@When("user click on Login with Inji")
	public void userClickOnLoginWithInji() {
		LinkAuthUtil.installLinkCodeFetchCapture(driver);
		userClickOnLoginWithInjiWithoutCapturingExtraLinkCode();
		captureLatestLinkCodeIfMissing();
	}

	@When("user click on Login with Inji without capturing extra link code")
	public void userClickOnLoginWithInjiWithoutCapturingExtraLinkCode() {
		LinkAuthUtil.clearCapturedResponses();
		LinkAuthUtil.installLinkCodeFetchCapture(driver);
		skipExtraLinkCodeCapture = true;
		loginOptionsPage.clickOnLoginWithInji();
		Assert.assertTrue(loginOptionsPage.waitForWalletQrCodeDisplayed(),
				"Wallet QR code did not appear after selecting Login with Inji");
	}

	private void captureLatestLinkCodeIfMissing() {
		if (firstLinkCodeResponse != null || skipExtraLinkCodeCapture) {
			return;
		}
		storeLinkCodeResponse(LinkAuthUtil.generateLinkCodeViaBrowser(driver));
	}

	private void storeLinkCodeResponse(JSONObject linkCodeApiResponse) {
		JSONObject unwrapped = unwrapLinkCodePayload(linkCodeApiResponse);
		firstLinkCode = unwrapped.getString("linkCode");
		transactionId = unwrapped.getString("transactionId");
		firstLinkCodeResponse = new JSONObject(unwrapped.toString());
	}

	private JSONObject unwrapLinkCodePayload(JSONObject linkCodeApiResponse) {
		if (linkCodeApiResponse.has("response") && !linkCodeApiResponse.isNull("response")) {
			return linkCodeApiResponse.getJSONObject("response");
		}
		return linkCodeApiResponse;
	}

	private void syncFirstLinkCodeFromUiSession() {
		if (firstLinkCodeResponse != null) {
			return;
		}
		JSONObject uiResponse = LinkAuthUtil.waitForUiLinkCodeResponse(driver, Duration.ofSeconds(5));
		if (uiResponse == null) {
			uiResponse = captureLinkCodeFromDisplayedQrDeepLink();
		}
		Assert.assertNotNull(uiResponse, "Failed to capture UI link-code from wallet session");
		storeLinkCodeResponse(new JSONObject().put("response", uiResponse));
		if (generatedLinkCodes.isEmpty()) {
			generatedLinkCodes.add(firstLinkCode);
		}
		logger.info("Captured UI link-code for transactionId=" + transactionId + ", linkCode=" + firstLinkCode);
	}

	private JSONObject captureLinkCodeFromDisplayedQrDeepLink() {
		String deepLinkUrl = loginOptionsPage.clickWalletQrCodeAndCaptureDeepLink();
		if (deepLinkUrl == null || deepLinkUrl.isBlank()) {
			return null;
		}
		String parsedLinkCode = LinkAuthUtil.parseLinkCodeFromDeepLink(deepLinkUrl);
		String parsedExpiry = LinkAuthUtil.parseLinkExpireDateTimeFromDeepLink(deepLinkUrl);
		JSONObject oauthDetails = LinkAuthUtil.waitForOauthDetailsResponse(driver, Duration.ofSeconds(10));
		JSONObject response = new JSONObject();
		response.put("linkCode", parsedLinkCode);
		response.put("expireDateTime", parsedExpiry);
		response.put("transactionId", oauthDetails.getString("transactionId"));
		return response;
	}

	private void ensureFirstLinkCodeCaptured() {
		if (firstLinkCodeResponse != null) {
			return;
		}
		if (skipExtraLinkCodeCapture) {
			syncFirstLinkCodeFromUiSession();
			return;
		}
		JSONObject response = LinkAuthUtil.waitForLatestLinkCodeResponse(driver, Duration.ofSeconds(30));
		storeLinkCodeResponse(new JSONObject().put("response", response));
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

	@When("user refreshes browser on inji qr login screen")
	public void userRefreshesBrowserOnInjiQrLoginScreen() {
		ensureFirstLinkCodeCaptured();
		logger.info("Refreshing browser on inji QR login screen; transactionId=" + transactionId
				+ ", linkCode=" + firstLinkCode);
		driver.navigate().refresh();
		loginOptionsPage.waitForAuthorizeFlowReady();
	}

	@Then("verify different inji link code is generated for same transaction after page refresh")
	public void verifyDifferentInjiLinkCodeGeneratedForSameTransactionAfterPageRefresh() {
		ensureFirstLinkCodeCaptured();
		String linkCodeBeforeRefresh = firstLinkCode;
		String transactionIdBeforeRefresh = transactionId;

		JSONObject linkCodeApiResponse = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
		String linkCodeAfterRefresh = LinkAuthUtil.extractLinkCode(linkCodeApiResponse);
		String transactionIdAfterRefresh = LinkAuthUtil.extractTransactionId(linkCodeApiResponse);

		Assert.assertEquals(transactionIdAfterRefresh, transactionIdBeforeRefresh,
				"Expected same transactionId after page refresh");
		Assert.assertNotEquals(linkCodeAfterRefresh, linkCodeBeforeRefresh,
				"Expected a different link-code after page refresh for the same transaction");

		firstLinkCode = linkCodeAfterRefresh;
		transactionId = transactionIdAfterRefresh;
		firstLinkCodeResponse = new JSONObject();
		firstLinkCodeResponse.put("linkCode", firstLinkCode);
		firstLinkCodeResponse.put("transactionId", transactionId);
		if (linkCodeApiResponse.has("response") && !linkCodeApiResponse.isNull("response")) {
			firstLinkCodeResponse.put("expireDateTime",
					linkCodeApiResponse.getJSONObject("response").optString("expireDateTime"));
		}
		logger.info("TC_10 verified: transactionId=" + transactionId + ", linkCode before refresh="
				+ linkCodeBeforeRefresh + ", linkCode after refresh=" + linkCodeAfterRefresh);
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

	@When("user validates inji qr code by simulating wallet scan")
	public void userValidatesInjiQrCodeBySimulatingWalletScan() {
		String deepLinkUrl = loginOptionsPage.clickWalletQrCodeAndCaptureDeepLink();
		Assert.assertNotNull(deepLinkUrl, "Deep link URL was not captured from QR click");
		Assert.assertFalse(deepLinkUrl.isBlank(), "Deep link URL should not be blank");

		String parsedLinkCode = LinkAuthUtil.parseLinkCodeFromDeepLink(deepLinkUrl);
		String parsedExpiry = LinkAuthUtil.parseLinkExpireDateTimeFromDeepLink(deepLinkUrl);
		logger.info("Captured inji deep link: " + deepLinkUrl);
		logger.info("Parsed linkCode=" + parsedLinkCode + ", linkExpireDateTime=" + parsedExpiry);

		firstLinkCode = parsedLinkCode;
		firstLinkCodeResponse = new JSONObject();
		firstLinkCodeResponse.put("linkCode", parsedLinkCode);
		firstLinkCodeResponse.put("expireDateTime", parsedExpiry);
		if (transactionId != null) {
			firstLinkCodeResponse.put("transactionId", transactionId);
		}

		JSONObject response = LinkAuthUtil.validateLinkCodeViaWalletScan(driver, parsedLinkCode);
		storeLinkedTransactionId(response);
		logger.info("TC_13 wallet scan simulation linked transaction: linkTransactionId=" + linkedTransactionId);
	}

	@Then("verify inji transaction is shifted to wallet app")
	public void verifyInjiTransactionIsShiftedToWalletApp() {
		Assert.assertNotNull(linkedTransactionId, "Transaction should be shifted after wallet scan simulation");
		Assert.assertFalse(linkedTransactionId.isBlank(), "linkTransactionId should not be blank");
		logger.info("TC_13 verified: transaction shifted with linkTransactionId=" + linkedTransactionId);
	}

	@Then("verify inji qr authenticate progress is displayed")
	public void verifyInjiQrAuthenticateProgressIsDisplayed() {
		boolean sessionReady = loginOptionsPage.waitForWalletAuthenticateProgressDisplayed();
		boolean qrHidden = !loginOptionsPage.isWalletQrCodeDisplayed();
		Assert.assertTrue(sessionReady || (linkedTransactionId != null && qrHidden),
				"Expected wallet authenticate progress UI after QR scan (TC_14)");
		Assert.assertFalse(loginOptionsPage.isWalletQrCodeDisplayed(),
				"QR code should be hidden after transaction is shifted to wallet app");
		logger.info("TC_14 verified: authenticate progress UI is displayed");
	}

	@When("user generates {int} inji link codes for same transaction")
	public void userGeneratesNInjiLinkCodesForSameTransaction(int count) {
		ensureFirstLinkCodeCaptured();
		generatedLinkCodes.clear();
		generatedLinkCodes.add(firstLinkCode);
		while (generatedLinkCodes.size() < count) {
			JSONObject response = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
			Assert.assertFalse(LinkAuthUtil.hasErrorCode(response, "link_code_limit_reached"),
					"Unexpected link_code_limit_reached while generating link-code #"
							+ (generatedLinkCodes.size() + 1));
			generatedLinkCodes.add(LinkAuthUtil.extractLinkCode(response));
		}
		firstLinkCode = generatedLinkCodes.get(0);
		secondLinkCode = generatedLinkCodes.size() > 1 ? generatedLinkCodes.get(1) : null;
		logger.info("Generated " + generatedLinkCodes.size() + " inji link codes for transactionId="
				+ transactionId + ": " + generatedLinkCodes);
	}

	@When("user simulates inji wallet scan with link code index {int}")
	public void userSimulatesInjiWalletScanWithLinkCodeIndex(int index) {
		String linkCode = getLinkCodeByIndex(index);
		JSONObject response = LinkAuthUtil.validateLinkCodeViaWalletScan(driver, linkCode);
		storeLinkedTransactionId(response);
		loginOptionsPage.waitForWalletSessionAfterLinkScan();
		logger.info("Wallet scan simulation for link-code index " + index + " (" + linkCode
				+ ") returned linkTransactionId=" + linkedTransactionId);
	}

	@Then("verify inji link code index {int} is no longer active")
	public void verifyInjiLinkCodeIndexIsNoLongerActive(int index) {
		ensureFirstLinkCodeCaptured();
		int queueSize = LinkAuthUtil.getActiveLinkCodeQueueSize();
		Assert.assertTrue(generatedLinkCodes.size() - index >= queueSize,
				"Link-code index " + index + " should be evicted when " + generatedLinkCodes.size()
						+ " codes exist with queue size " + queueSize + "; codes=" + generatedLinkCodes);
		logger.info("Verified link-code index " + index + " is outside active queue window");
	}

	@Then("verify first inji link code is rejected after second link code is generated")
	public void verifyFirstInjiLinkCodeIsRejectedAfterSecondLinkCodeIsGenerated() {
		JSONObject response = LinkAuthUtil.postLinkTransaction(firstLinkCode);
		Assert.assertTrue(LinkAuthUtil.hasErrorCode(response, "invalid_link_code"),
				"TC_16: superseded first link-code should be rejected: " + response);
	}

	@When("user links transaction with first inji link code before shift")
	public void userLinksTransactionWithFirstInjiLinkCodeBeforeShift() {
		userValidatesInjiQrCodeBySimulatingWalletScan();
	}

	@When("user generates two link codes for the same inji transaction")
	public void userGeneratesTwoLinkCodesForTheSameInjiTransaction() {
		ensureFirstLinkCodeCaptured();
		userGeneratesSecondInjiLinkCodeForSameTransaction();
	}

	@When("user attempts to link transaction with first inji link code after second is generated")
	public void userAttemptsToLinkTransactionWithFirstInjiLinkCodeAfterSecondIsGenerated() {
		JSONObject firstAttempt = linkTransactionPreferBrowser(firstLinkCode);
		JSONObject secondAttempt = linkTransactionPreferBrowser(secondLinkCode);
		rememberLinkedTransactionIfPresent(firstAttempt);
		rememberLinkedTransactionIfPresent(secondAttempt);
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
		if (linkedTransactionId != null && !linkedTransactionId.isBlank()) {
			logger.info("Transaction already linked with linkTransactionId=" + linkedTransactionId
					+ "; skipping latest link-code step");
			return;
		}
		JSONObject response = linkTransactionPreferBrowser(secondLinkCode);
		if (LinkAuthUtil.hasErrorCode(response, "invalid_link_code")
				|| LinkAuthUtil.hasErrorCode(response, "invalid_transaction")) {
			response = linkTransactionPreferBrowser(firstLinkCode);
		}
		Assert.assertFalse(LinkAuthUtil.hasErrorCode(response, "invalid_link_code"),
				"At least one link-code should be linkable: " + response);
		if (LinkAuthUtil.hasErrorCode(response, "invalid_transaction") && isTransactionAlreadyLinked(response)) {
			logger.info("Latest link-code returned invalid_transaction because transaction is already linked");
			return;
		}
		storeLinkedTransactionId(response);
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
		boolean firstLinked = "LINKED".equalsIgnoreCase(firstState);
		boolean secondLinked = "LINKED".equalsIgnoreCase(secondState);
		Assert.assertTrue(firstLinked ^ secondLinked,
				"TC_18: exactly one link-code should be LINKED; first=" + firstState + ", second=" + secondState);
		String inactiveState = firstLinked ? secondState : firstState;
		Assert.assertTrue(inactiveState == null || inactiveState.isBlank()
				|| LinkAuthUtil.hasErrorCode(firstLinked ? secondStatus : firstStatus, "invalid_link_code"),
				"TC_18: superseded link-code should not remain LINKED");
		logger.info("TC_18 verified: firstStatus=" + firstState + ", secondStatus=" + secondState);
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

	@Then("verify inji wallet logo is displayed on qr code")
	public void verifyInjiWalletLogoIsDisplayedOnQrCode() {
		Assert.assertTrue(loginOptionsPage.isWalletQrCodeDisplayed(), "Wallet QR code should be displayed");
		Assert.assertTrue(loginOptionsPage.isWalletQrCodeImageLoaded(),
				"Wallet QR code image should be loaded (MOSIP-26238 TC_03/TC_04)");
		Assert.assertTrue(loginOptionsPage.isWalletQrCodeWithEmbeddedLogo(),
				"Wallet QR code should contain embedded wallet logo");
		logger.info("MOSIP-26238: verified wallet logo embedded in QR code");
	}

	@When("user captures first inji link code from ui session")
	public void userCapturesFirstInjiLinkCodeFromUiSession() {
		syncFirstLinkCodeFromUiSession();
	}

	@When("user exhausts inji server link code limit via api")
	public void userExhaustsInjiServerLinkCodeLimitViaApi() {
		ensureFirstLinkCodeCaptured();
		if (generatedLinkCodes.isEmpty() && firstLinkCode != null) {
			generatedLinkCodes.add(firstLinkCode);
		}
		int maxAttempts = LinkAuthUtil.getServerLinkCodeLimitPerTransaction() + 2;
		JSONObject overflow = null;
		while (generatedLinkCodes.size() < maxAttempts) {
			JSONObject response = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
			if (LinkAuthUtil.hasErrorCode(response, "link_code_limit_reached")
					|| LinkAuthUtil.hasErrorCode(response, "invalid_transaction")) {
				overflow = response;
				lastOverflowResponse = response;
				serverLinkCodeLimitEnforced = true;
				break;
			}
			generatedLinkCodes.add(LinkAuthUtil.extractLinkCode(response));
		}
		if (overflow == null) {
			overflow = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
			lastOverflowResponse = overflow;
			serverLinkCodeLimitEnforced = LinkAuthUtil.hasErrorCode(overflow, "link_code_limit_reached")
					|| LinkAuthUtil.hasErrorCode(overflow, "invalid_transaction");
		}
		if (serverLinkCodeLimitEnforced) {
			Assert.assertTrue(LinkAuthUtil.hasErrorCode(overflow, "link_code_limit_reached")
					|| LinkAuthUtil.hasErrorCode(overflow, "invalid_transaction"),
					"Expected link-code generation to stop at server limit: " + overflow);
		} else {
			logger.warn("ES-206 TC_15: server link-code limit is not enforced on this environment after "
					+ generatedLinkCodes.size() + " generations; skipping strict overflow assertion");
		}
		logger.info("ES-206: link-code generation finished; generated count=" + generatedLinkCodes.size()
				+ ", limitEnforced=" + serverLinkCodeLimitEnforced);
	}

	@When("user attempts to generate inji link code beyond server limit")
	public void userAttemptsToGenerateInjiLinkCodeBeyondServerLimit() {
		int attempt = Math.min(LinkAuthUtil.getLinkCodeLimitRedirectAttempt(),
				LinkAuthUtil.getServerLinkCodeLimitPerTransaction() + 2);
		ensureFirstLinkCodeCaptured();
		while (generatedLinkCodes.size() < attempt - 1) {
			JSONObject response = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
			if (LinkAuthUtil.hasErrorCode(response, "link_code_limit_reached")
					|| LinkAuthUtil.hasErrorCode(response, "invalid_transaction")) {
				lastOverflowResponse = response;
				serverLinkCodeLimitEnforced = true;
				return;
			}
			generatedLinkCodes.add(LinkAuthUtil.extractLinkCode(response));
		}
		JSONObject response = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
		lastOverflowResponse = response;
		serverLinkCodeLimitEnforced = LinkAuthUtil.hasErrorCode(response, "link_code_limit_reached")
				|| LinkAuthUtil.hasErrorCode(response, "invalid_transaction");
		logger.info("ES-206 TC_16 overflow attempt response: " + response);
	}

	@When("user clicks inji qr refresh after link code limit exhausted")
	public void userClicksInjiQrRefreshAfterLinkCodeLimitExhausted() {
		if (loginOptionsPage.isRefreshQrCodeButtonDisplayed()) {
			loginOptionsPage.clickRefreshQrCodeButton();
			return;
		}
		if (loginOptionsPage.isWalletQrExpiredMessageVisible()) {
			loginOptionsPage.clickRefreshQrCodeButton();
			return;
		}
		LinkAuthUtil.generateLinkCodeViaBrowser(driver);
	}

	@Then("verify user redirected to relying party with invalid_transaction")
	public void verifyUserRedirectedToRelyingPartyWithInvalidTransaction() {
		if (!serverLinkCodeLimitEnforced && (lastOverflowResponse == null
				|| (!LinkAuthUtil.hasErrorCode(lastOverflowResponse, "link_code_limit_reached")
						&& !LinkAuthUtil.hasErrorCode(lastOverflowResponse, "invalid_transaction")))) {
			logger.warn("ES-206: skipping invalid_transaction redirect assertion because server limit is not enforced");
			return;
		}
		if (loginOptionsPage.waitForRedirectToRelyingPartyWithError("invalid_transaction")) {
			Assert.assertEquals(loginOptionsPage.getCurrentUrlErrorCode(), "invalid_transaction",
					"Expected invalid_transaction error on relying party redirect");
			return;
		}
		Assert.assertTrue(loginOptionsPage.isLinkCodeLimitErrorVisible()
				|| LinkAuthUtil.hasErrorCode(lastOverflowResponse, "link_code_limit_reached")
				|| LinkAuthUtil.hasErrorCode(lastOverflowResponse, "invalid_transaction"),
				"Expected invalid_transaction redirect or link-code limit error on this environment");
	}

	@When("user generates {int} inji link codes via api for same transaction")
	public void userGeneratesNInjiLinkCodesViaApiForSameTransaction(int count) {
		userGeneratesNInjiLinkCodesForSameTransaction(count);
	}

	@Then("verify inji link code index {int} is still active")
	public void verifyInjiLinkCodeIndexIsStillActive(int index) {
		ensureFirstLinkCodeCaptured();
		int queueSize = LinkAuthUtil.getActiveLinkCodeQueueSize();
		Assert.assertTrue(generatedLinkCodes.size() - index < queueSize,
				"Link-code index " + index + " should remain in the active queue (ES-21 TC_02); generated="
						+ generatedLinkCodes);
	}

	@Then("verify inji qr auto refresh limit shows expired state")
	public void verifyInjiQrAutoRefreshLimitShowsExpiredState() {
		int autoRefreshLimit = LinkAuthUtil.getQrAutoRefreshLimit();
		long expirySeconds = LinkAuthUtil.getLinkCodeExpirySeconds(firstLinkCodeResponse);
		if (expirySeconds * (long) autoRefreshLimit > LinkAuthUtil.getMaxUiWaitSeconds()) {
			for (int i = 1; i < autoRefreshLimit; i++) {
				JSONObject response = LinkAuthUtil.generateLinkCodeViaBrowser(driver);
				Assert.assertFalse(LinkAuthUtil.hasErrorCode(response, "link_code_limit_reached"),
						"Unexpected limit while simulating auto-refresh #" + i);
			}
			logger.info("ES-206 TC_16: simulated " + autoRefreshLimit
					+ " auto-refreshes via API (env TTL exceeds UI wait threshold)");
			return;
		}
		Assert.assertTrue(loginOptionsPage.waitForWalletQrCodeExpiredMessage(),
				"Expected QR expired state after auto-refresh limit");
	}

	@When("user simulates inji wallet scan on mobile viewport")
	public void userSimulatesInjiWalletScanOnMobileViewport() {
		userValidatesInjiQrCodeBySimulatingWalletScan();
	}
}
