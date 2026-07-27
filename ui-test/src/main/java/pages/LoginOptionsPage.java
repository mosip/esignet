package pages;

import base.BasePage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import utils.EsignetConfigManager;
import utils.LinkAuthUtil;
import utils.MockMdsManager;
import utils.ResourceBundleLoader;

public class LoginOptionsPage extends BasePage {

	private static final Set<String> NON_WALLET_LOGIN_IDS = Set.of(
			"login_with_otp", "login_with_bio", "login_with_pwd", "login_with_pin", "login_with_kbi");

	public LoginOptionsPage(WebDriver driver) {
		super(driver);
	}

	@FindBy(id = "signup-url-button")
	WebElement loginButton;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(id = "login_with_walletname")
	WebElement loginWithInji;

	@FindBy(id = "language_selection")
	WebElement languageDropdown;

	@FindBy(id = "hi1")
	WebElement hindiLanguage;

	@FindBy(id = "login_with_otp")
	WebElement loginWithOtpBtn;

	@FindBy(id = "login_with_bio")
	WebElement loginWithBiometricBtn;

	@FindBy(id = "login_with_walletname")
	WebElement loginWithInjiBtn;

	@FindBy(id = "login_with_pwd")
	WebElement loginWithPasswordBtn;

	@FindBy(id = "login_with_pin")
	WebElement loginWithPinBtn;

	@FindBy(id = "login_with_kbi")
	WebElement loginWithKbiBtn;

	@FindBy(id = "show-more-options")
	List<WebElement> moreWaysToSignIn;

	@FindBy(id = "mobile")
	WebElement mobileNumberOption;

	@FindBy(id = "nrc")
	WebElement nrcIdOption;

	@FindBy(id = "vid")
	WebElement vidOption;

	@FindBy(id = "email")
	WebElement emailOption;

	@FindBy(id = "back-button")
	WebElement backButton;

	@FindBy(id = "login-header")
	WebElement loginHeader;

	@FindBy(id = "login-subheader")
	WebElement loginSubHeader;

	@FindBy(xpath = "//div[contains(@class,'font-semibold') and contains(@class,'mx-2')]")
	WebElement selectPreferredIdHeader;

	@FindBy(id = "get_otp")
	WebElement getOtpButton;

	@FindBy(xpath = "//button[@id='mobile' and contains(@class,'selected_login_id')]")
	WebElement mobileSelected;

	@FindBy(id = "Otp_login_dropdown_button")
	WebElement prefixNumberField;

	@FindBy(id = "KHM")
	WebElement khmCountryCode;

	@FindBy(id = "IND")
	WebElement indCountryCode;

	@FindBy(id = "otp_verify_input")
	WebElement otpInputField;

	@FindBy(xpath = "//div[contains(@class,'header my-2')]")
	WebElement attentionScreen;

	@FindBy(id = "cancel-button")
	WebElement attentionCancelButton;

	@FindBy(id = "discontinue-button")
	WebElement attentionDiscontinueButton;

	@FindBy(id = "Otp_vid")
	WebElement vidField;

	@FindBy(id = "error-banner-message")
	WebElement invalidIndividualIdErrorMessage;

	@FindBy(id = "Otp_email")
	WebElement emailField;

	@FindBy(id = "sbi_vid")
	WebElement biometricVidField;

	@FindBy(id = "secure-biometric-interface-integration")
	WebElement biometricIntegrationContainer;

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo, "Verified is logo displayed");
	}

	public void waitForSignInPageReady() {
		waitForAuthorizeFlowReady();
	}

	public void waitForAuthorizeFlowReady() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		wait.until(webDriver -> {
			String url = webDriver.getCurrentUrl();
			int hashIndex = url.indexOf('#');
			if (hashIndex >= 0 && hashIndex < url.length() - 10) {
				return true;
			}
			if (findVisibleWalletLoginButton() != null) {
				return true;
			}
			if (!webDriver.findElements(By.id("login_with_otp")).isEmpty()) {
				return true;
			}
			return !webDriver.findElements(By.id("show-more-options")).isEmpty();
		});
	}

	public void clickOnLoginWithInji() {
		WebElement walletLoginButton = waitForWalletLoginButton();
		clickOnElement(walletLoginButton, "Clicked on login with wallet");
	}

	public boolean isLoginWithInjiOptionVisible() {
		return waitForWalletLoginButton(Duration.ofSeconds(30)) != null;
	}

	public void openLoginWithInjiViaMoreWaysToSignInIfNeeded() {
		waitForAuthorizeFlowReady();
		if (waitForWalletLoginButton(Duration.ofSeconds(15)) == null) {
			clickMoreWaysToSignInIfVisible();
		}
		if (waitForWalletLoginButton(Duration.ofSeconds(15)) == null) {
			clickMoreWaysToSignInIfVisible();
		}
	}

	private WebElement waitForWalletLoginButton(Duration timeout) {
		WebDriverWait wait = new WebDriverWait(driver, timeout);
		try {
			return wait.until(webDriver -> findVisibleWalletLoginButton());
		} catch (TimeoutException e) {
			return null;
		}
	}

	private WebElement waitForWalletLoginButton() {
		WebElement walletLoginButton = waitForWalletLoginButton(Duration.ofSeconds(30));
		if (walletLoginButton == null) {
			throw new TimeoutException("Wallet login option was not displayed");
		}
		return walletLoginButton;
	}

	private WebElement findVisibleWalletLoginButton() {
		for (WebElement button : driver.findElements(By.cssSelector("[id^='login_with_']"))) {
			String id = button.getAttribute("id");
			if (id != null && !NON_WALLET_LOGIN_IDS.contains(id) && button.isDisplayed()) {
				return button;
			}
		}
		if (loginWithInjiBtn != null) {
			try {
				if (loginWithInjiBtn.isDisplayed()) {
					return loginWithInjiBtn;
				}
			} catch (StaleElementReferenceException ignored) {
				// fall through to null
			}
		}
		return null;
	}

	public boolean waitForWalletQrCodeDisplayed() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		try {
			wait.until(driver -> isWalletQrCodeDisplayed());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean isWalletQrCodeDisplayed() {
		try {
			WebElement qrCode = waitForElementVisible(By.id("wallet-qr-code"));
			return qrCode.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isWalletQrHeaderDisplayed() {
		List<WebElement> headers = driver.findElements(By.cssSelector(".qr-title"));
		for (WebElement header : headers) {
			if (!header.isDisplayed()) {
				continue;
			}
			String text = normalizeMessage(safeGetText(header));
			if (text.contains("scan") && text.contains("qr")) {
				return true;
			}
		}
		String bundleHeader = normalizeMessage(ResourceBundleLoader.get("LoginQRCode.wallet_header"));
		if (!bundleHeader.startsWith("!!missing_key:")) {
			String prefix = normalizeMessage(bundleHeader.split("\\{\\{walletname\\}\\}")[0]);
			if (!prefix.isBlank()) {
				for (WebElement header : headers) {
					if (header.isDisplayed() && normalizeMessage(safeGetText(header)).contains(prefix)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean isDontHaveWalletFooterDisplayed() {
		List<WebElement> footers = driver.findElements(
				By.xpath("//p[contains(@class,'text-center') and contains(@class,'font-semibold')]"));
		for (WebElement footer : footers) {
			if (!footer.isDisplayed()) {
				continue;
			}
			String text = normalizeMessage(safeGetText(footer));
			if (text.contains("don't have") || text.contains("dont have")) {
				return true;
			}
		}
		return false;
	}

	public boolean isDownloadNowLinkDisplayed() {
		try {
			WebElement downloadLink = waitForElementVisible(By.id("download_now"));
			return downloadLink.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	public String getDownloadNowLinkHref() {
		WebElement downloadLink = waitForElementVisible(By.id("download_now"));
		return downloadLink.getAttribute("href");
	}

	public String getWalletQrCodeSrc() {
		WebElement qrCode = waitForElementVisible(By.id("wallet-qr-code"));
		return qrCode.getAttribute("src");
	}

	public boolean waitForWalletQrCodeExpiredMessage() {
		int waitSeconds = LinkAuthUtil.getConfiguredLinkCodeExpireSeconds()
				+ parseTimeoutProperty("injiQrExpiredUiWaitBufferSeconds", 90);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> isWalletQrExpiredMessageVisible());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean isWalletQrExpiredMessageVisible() {
		String bannerText = getVisibleErrorBannerText();
		if (bannerText.contains("expired")) {
			return true;
		}
		String expectedMessage = ResourceBundleLoader.get("errors.wallet.qr_code_expired");
		return !expectedMessage.startsWith("!!MISSING_KEY:")
				&& bannerText.contains(normalizeMessage(expectedMessage));
	}

	public boolean isRefreshQrCodeButtonDisplayed() {
		List<WebElement> refreshButtons = driver.findElements(By.id("refresh_qr_code"));
		return refreshButtons.stream().anyMatch(WebElement::isDisplayed);
	}

	public void clickRefreshQrCodeButton() {
		WebElement refreshButton = waitForElementVisible(By.id("refresh_qr_code"));
		clickOnElement(refreshButton, "Clicked refresh QR code button");
	}

	public boolean waitForWalletQrCodeSrcChange(String previousSrc) {
		int waitSeconds = LinkAuthUtil.getConfiguredLinkCodeExpireSeconds()
				+ parseTimeoutProperty("injiQrExpiredUiWaitBufferSeconds", 90);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> {
				List<WebElement> qrCodes = driver.findElements(By.id("wallet-qr-code"));
				for (WebElement qrCode : qrCodes) {
					if (qrCode.isDisplayed()) {
						String currentSrc = qrCode.getAttribute("src");
						return currentSrc != null && !currentSrc.isBlank() && !currentSrc.equals(previousSrc);
					}
				}
				return false;
			});
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean waitForLinkAuthWaitingMessage(String walletName) {
		int waitSeconds = parseTimeoutProperty("injiLinkAuthWaitingTimeoutSeconds", 30);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		String expectedFragment = normalizeMessage(walletName);
		try {
			wait.until(driver -> {
				String containerText = normalizeMessage(safeGetText(driver.findElement(By.tagName("body"))));
				return containerText.contains("authenticate") && containerText.contains(expectedFragment);
			});
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean isLinkAuthWaitingIndicatorDisplayed() {
		List<WebElement> indicators = driver.findElements(By.cssSelector(".loading-indicator"));
		return indicators.stream().anyMatch(WebElement::isDisplayed);
	}

	public boolean isLanguageDropdownDisplayed() {
		return isElementVisible(languageDropdown, "Verified language dropdown is visible");
	}

	public void clickOnLanguageDropdown() {
		clickOnElement(languageDropdown, "Clicked on language dropdown");
	}

	public void clickOnHindiLanguage() {
		clickOnElement(hindiLanguage, "Selected hindi language from dropdown");
	}

	public boolean isSelectedLanguageDisplayed() {
		return isElementVisible(loginWithOtpBtn, "Verified selected language displayed");
	}

	public boolean isLoginWithBiometicDisplayed() {
		return isElementVisible(loginWithBiometricBtn, "Verified login with biometric button is displayed");
	}

	public boolean isLoginWithInjiDisplayed() {
		return isElementVisible(loginWithInjiBtn, "Verified login with inji button is displayed");
	}

	public boolean isLoginWithPasswordDisplayed() {
		return isElementVisible(loginWithPasswordBtn, "Verified login with password button is displayed");
	}

	public List<WebElement> getLoginOptions() {
		List<WebElement> options = new ArrayList<>();
		options.add(loginWithOtpBtn);
		options.add(loginWithBiometricBtn);
		options.add(loginWithInjiBtn);
		options.add(loginWithPasswordBtn);
		options.add(loginWithPinBtn);
		options.add(loginWithKbiBtn);
		return options;
	}

	public boolean isMoreWaysToSignInOptionDisplayed() {
		return !moreWaysToSignIn.isEmpty() && moreWaysToSignIn.get(0).isDisplayed();
	}

	public Map<String, WebElement> getAcrToElementMap() {
		Map<String, WebElement> map = new HashMap<>();
		map.put("PWD", loginWithPasswordBtn);
		map.put("OTP", loginWithOtpBtn);
		map.put("BIO", loginWithBiometricBtn);
		map.put("WLA", loginWithInjiBtn);
		map.put("PIN", loginWithPinBtn);
		map.put("KBI", loginWithKbiBtn);
		return map;
	}

	public void selectLanguage(String language) {
		WebElement langOption = waitForElementVisible(
				By.xpath("//div[@role='menuitem' and normalize-space()='" + language + "']"));

		langOption.click();
	}

	public boolean isUILanguageChanged(String text) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		wait.until(ExpectedConditions.textToBePresentInElement(loginButton, text));
		return loginButton.getText().contains(text);
	}

	public WebElement getLoginWithOtpButton() {
		return loginWithOtpBtn;
	}

	public String getLoginWithOtpButtonText() {
		waitForElementVisible(loginWithOtpBtn);
		return loginWithOtpBtn.getText().trim();
	}

	public boolean isMobileNumberOptionDisplayed() {
		return isElementVisible(mobileNumberOption, "Verified mobile number option is displayed for authentication");
	}

	public boolean isNrcIdOptionDisplayed() {
		return isElementVisible(nrcIdOption, "Verified nrc option is displayed for authentication");
	}

	public boolean isVidOptionDisplayed() {
		return isElementVisible(vidOption, "Verified vid option is displayed for authentication");
	}

	public boolean isEmailOptionDisplayed() {
		return isElementVisible(emailOption, "Verified email option is displayed for authentication");
	}

	public boolean isBackButtonDisplayed() {
		return isElementVisible(backButton, "Verified back button is visible for return to Select a preferred mode");
	}

	public void clickOnBackButton() {
		clickOnElement(backButton, "Clicked on back button");
	}

	public void clickOnLoginWithBiometric() {
		clickOnElement(loginWithBiometricBtn, "Clicked on login with biometrics");
	}

	public void clickOnLoginWithPassword() {
		clickOnElement(loginWithPasswordBtn, "Clicked on login with password");
	}

	public boolean isGetOtpButtonEnabled() {
		return isButtonEnabled(getOtpButton, "Verified get otp button is enabled");
	}

	public boolean isMobileNumberSelected() {
		return isElementVisible(mobileSelected, "Verified mobile number seleted in authentication screen");
	}

	public boolean isKhmCountryCodePrefixDisplayed() {
		return isElementVisible(khmCountryCode, "Verified khm country code prefix is displayed");
	}

	public boolean isIndCountryCodePrefixDisplayed() {
		return isElementVisible(indCountryCode, "Verified ind country code prefix is displayed");
	}

	public void clickOnPrefixNumberFieldButton() {
		clickOnElement(prefixNumberField, "Clicked on Prefix Number Field button");
	}

	public void clickOnIndCountryCodePrefix() {
		clickOnElement(indCountryCode, "Clicked on ind country code prefix button");
	}

	public void clickOnKhmCountryCodePrefix() {
		clickOnElement(khmCountryCode, "Clicked on khm country code prefix button");
	}

	public boolean isOtpInputFieldIsDisplayed() {
		return isElementVisible(otpInputField, "Verified otp input field is displayed");
	}

	public boolean isAttentionScreenIsDisplayed() {
		return isElementVisible(attentionScreen, "Verified attention screen is displayed");
	}

	public void clickOnAttentionCancelButton() {
		clickOnElement(attentionCancelButton, "Clicked on attention cancel button");
	}

	public void clickOnAttentionDiscontinueButton() {
		clickOnElement(attentionDiscontinueButton, "Clicked on attention discontinue button");
	}

	public void clickOnVidOptionButton() {
		clickOnElement(vidOption, "Clicked on vid option button");
	}

	public boolean isInvalidIndividualIdErrorMessageIsDisplayed() {
		return isElementVisible(invalidIndividualIdErrorMessage,
				"Verified invalid individual id error message is displayed");
	}

	public void enterVid(String vid) {
		waitForElementVisible(vidField);
		vidField.clear();
		enterText(vidField, vid, "Entered vid in vid field");
	}

	public void clickOnEmailOptionButton() {
		clickOnElement(emailOption, "Clicked on email option button");
	}

	public void enterEmail(String email) {
		waitForElementVisible(emailField);
		emailField.clear();
		enterText(emailField, email, "Entered email in email field");
	}

	public boolean isBiometricIntegrationContainerDisplayed() {
		return isElementVisible(biometricIntegrationContainer,
				"Verified secure biometric interface integration container is displayed");
	}

	public boolean isBiometricScreenActive() {
		return isBiometricIntegrationContainerVisibleNow()
				&& (isBiometricVidOptionVisibleNow() || isBiometricVidTextFieldVisibleNow());
	}

	private boolean isBiometricIntegrationContainerVisibleNow() {
		List<WebElement> containers = driver.findElements(By.id("secure-biometric-interface-integration"));
		return !containers.isEmpty() && containers.get(0).isDisplayed();
	}

	private boolean isBiometricVidOptionVisibleNow() {
		List<WebElement> options = driver.findElements(By.id("vid"));
		return !options.isEmpty() && options.get(0).isDisplayed();
	}

	private boolean isBiometricVidTextFieldVisibleNow() {
		List<WebElement> fields = driver.findElements(By.id("sbi_vid"));
		return !fields.isEmpty() && fields.get(0).isDisplayed();
	}

	public boolean isBiometricVidOptionDisplayed() {
		return isElementVisible(vidOption, "Verified UIN/VID option is displayed on biometric screen");
	}

	public void clickOnBiometricVidOptionButton() {
		clickOnElement(vidOption, "Clicked on UIN/VID option on biometric screen");
	}

	/**
	 * SBI widget re-renders after Mock MDS retry can hide the UIN/VID input until the tab is selected again.
	 */
	public void ensureBiometricVidFieldVisible() {
		if (isBiometricVidTextFieldVisibleNow()) {
			return;
		}
		if (isBiometricVidOptionVisibleNow()) {
			clickOnBiometricVidOptionButton();
		}
		waitForElementVisible(biometricVidField);
	}

	public boolean isBiometricVidTextFieldDisplayed() {
		return isElementVisible(biometricVidField, "Verified VID text field is displayed on biometric screen");
	}

	private static final String SCANNING_DEVICES_MSG_KEY = "loadingMsgs.scanning_devices_msg";
	private volatile long lastBiometricRescanAttemptMs;
	private volatile boolean rescanActivitySeen;

	public boolean waitForScanningDevicesOrDeviceDiscovered() {
		int waitSeconds = Math.max(getBiometricScanningWaitSeconds(), getBiometricDeviceDiscoveryTimeoutSeconds());
		long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
		while (System.currentTimeMillis() < deadline) {
			if (isScanningDevicesMessageVisible() || isBiometricDeviceDiscovered()) {
				return true;
			}
			if (MockMdsManager.isRunning() && isBrowserSbiDeviceCachePopulated()) {
				return true;
			}
			if (lastBiometricRescanAttemptMs > 0) {
				if (isScanningDevicesMessageVisible() || isBiometricDeviceDiscovered()
						|| isDeviceNotFoundMessageVisible()
						|| (MockMdsManager.isRunning() && isBrowserSbiDeviceCachePopulated())) {
					rescanActivitySeen = true;
				}
			}
			if (isRecentBiometricRescanCompleted()) {
				return true;
			}
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return isBiometricDeviceDiscovered() || isRecentBiometricRescanCompleted();
	}

	private boolean isRecentBiometricRescanCompleted() {
		if (lastBiometricRescanAttemptMs <= 0
				|| System.currentTimeMillis() - lastBiometricRescanAttemptMs > getBiometricDeviceDiscoveryTimeoutSeconds()
						* 1000L) {
			return false;
		}
		if (!rescanActivitySeen) {
			return false;
		}
		if (isBiometricDeviceDiscovered()) {
			return true;
		}
		if (isScanningDevicesMessageVisible()) {
			return true;
		}
		if (MockMdsManager.isRunning() && isBrowserSbiDeviceCachePopulated()) {
			return true;
		}
		return !MockMdsManager.isRunning() && isDeviceNotFoundMessageVisible();
	}

	private boolean isScanningDevicesMessageVisible() {
		return isLocalizedTextVisibleWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY)
				|| isTextVisibleWithinBiometricContainer("scanning devices");
	}

	public boolean isScanningDevicesMessageDisplayed() {
		return waitForLocalizedTextWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY, getBiometricScanningWaitSeconds());
	}

	public boolean isRetryScanButtonNotDisplayedWhileScanning() {
		if (!waitForLocalizedTextWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY, 5)) {
			return true;
		}
		return !isRetryScanButtonVisible();
	}

	public boolean waitForDeviceNotFoundMessageDisplayed() {
		int waitSeconds = getBiometricDeviceDiscoveryTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> isDeviceNotFoundMessageVisible());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public void clickOnBiometricDeviceScanRetryButton() {
		if (isBiometricDeviceDiscovered()) {
			return;
		}
		int waitSeconds = getBiometricDeviceDiscoveryTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			WebElement retryButton = wait.until(driver -> findDisplayedRetryButton());
			clearBrowserSbiDeviceCache();
			lastBiometricRescanAttemptMs = System.currentTimeMillis();
			rescanActivitySeen = true;
			clickOnElement(retryButton, "Clicked on biometric device scan retry button");
			triggerBrowserSbiDiscovery();
			return;
		} catch (TimeoutException e) {
			if (isBiometricDeviceDiscovered()) {
				return;
			}
			forceBrowserBiometricRescan();
		}
	}

	public void syncBiometricWidgetIfMockMdsRunning() {
		if (!MockMdsManager.isRunning() || !isBiometricScreenActive()) {
			return;
		}
		long deadline = System.currentTimeMillis() + getBiometricDeviceDiscoveryTimeoutSeconds() * 1000L;
		while (System.currentTimeMillis() < deadline && !isBiometricDeviceDiscovered()) {
			injectMockMdsDeviceCacheIfRunning();
			triggerBiometricRescanViaWidget();
			if (findDisplayedRetryButton() != null) {
				clickOnBiometricDeviceScanRetryButton();
				break;
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		if (!isBiometricDeviceDiscovered()) {
			reenterBiometricLoginAfterMockMdsStart();
		}
	}

	/**
	 * When Mock MDS starts after the widget already scanned with no device, going back and re-opening
	 * biometric login forces a fresh SBI discovery pass (more reliable than retry alone).
	 */
	public void reenterBiometricLoginAfterMockMdsStart() {
		if (!MockMdsManager.isRunning()) {
			return;
		}
		try {
			List<WebElement> backButtons = driver.findElements(By.id("back-button"));
			for (WebElement backButton : backButtons) {
				if (backButton.isDisplayed()) {
					clickOnElement(backButton, "Navigated back to re-enter biometric login after Mock MDS start");
					break;
				}
			}
			if (isLoginWithBiometricsOptionVisible()) {
				clickOnElement(loginWithBiometricBtn, "Re-opened Login with Biometrics after Mock MDS start");
			}
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(getBiometricScanningWaitSeconds()));
			wait.until(driver -> isBiometricIntegrationContainerVisibleNow());
			if (isBiometricVidOptionVisibleNow()) {
				clickOnBiometricVidOptionButton();
			}
			lastBiometricRescanAttemptMs = System.currentTimeMillis();
			rescanActivitySeen = true;
			triggerBrowserSbiDiscovery();
			injectMockMdsDeviceCacheIfRunning();
		} catch (Exception e) {
			forceBrowserBiometricRescan();
		}
	}

	public void forceBrowserBiometricRescan() {
		lastBiometricRescanAttemptMs = System.currentTimeMillis();
		rescanActivitySeen = true;
		clearBrowserSbiDeviceCache();
		triggerBrowserSbiDiscovery();
		injectMockMdsDeviceCacheIfRunning();
		if (!triggerBiometricRescanViaWidget() && isBiometricVidOptionVisibleNow()) {
			clickOnBiometricVidOptionButton();
		}
	}

	private void injectMockMdsDeviceCacheIfRunning() {
		if (!MockMdsManager.isRunning() || MockMdsManager.getActivePort() <= 0) {
			return;
		}
		java.util.Map<String, String> cacheEntries = MockMdsManager
				.buildBrowserSbiCacheEntries(MockMdsManager.getActivePort());
		if (cacheEntries.isEmpty()) {
			return;
		}
		try {
			((JavascriptExecutor) driver).executeScript(
					"const entries = arguments[0];"
							+ "try {"
							+ "  if (entries.discover) { localStorage.setItem('discover', entries.discover); }"
							+ "  if (entries.deviceInfo) { localStorage.setItem('deviceInfo', entries.deviceInfo); }"
							+ "  window.dispatchEvent(new Event('storage'));"
							+ "} catch (e) {}",
					cacheEntries);
			rescanActivitySeen = true;
		} catch (Exception ignored) {
			// Best-effort cache seed before widget rescan.
		}
	}

	private void triggerBrowserSbiDiscovery() {
		int scriptTimeoutSeconds = Math.max(getBiometricDeviceDiscoveryTimeoutSeconds(), 45);
		try {
			driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(scriptTimeoutSeconds));
			((JavascriptExecutor) driver).executeAsyncScript(
					"const done = arguments[arguments.length - 1];"
							+ "const decodeJwtPayload = (token) => {"
							+ "  try {"
							+ "    const payload = token.split('.')[1];"
							+ "    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');"
							+ "    const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4);"
							+ "    return JSON.parse(atob(padded));"
							+ "  } catch (e) { return null; }"
							+ "};"
							+ "const isValidDevice = (info) => info && info.certification === 'L1'"
							+ "  && info.purpose === 'Auth' && info.deviceStatus === 'Ready';"
							+ "const ports = Array.from({length: 10}, (_, index) => 4501 + index);"
							+ "const discover = {};"
							+ "const deviceInfo = {};"
							+ "Promise.all(ports.map(async (port) => {"
							+ "  try {"
							+ "    const discResponse = await fetch('http://127.0.0.1:' + port + '/device', {"
							+ "      method: 'MOSIPDISC',"
							+ "      headers: {'Content-Type': 'application/json'},"
							+ "      body: JSON.stringify({type: 'Biometric Device'})"
							+ "    });"
							+ "    if (!discResponse.ok) { return; }"
							+ "    const discData = await discResponse.json();"
							+ "    discover[port] = discData;"
							+ "    const infoResponse = await fetch('http://127.0.0.1:' + port + '/info', {"
							+ "      method: 'MOSIPDINFO'"
							+ "    });"
							+ "    if (!infoResponse.ok) { return; }"
							+ "    const infoData = await infoResponse.json();"
							+ "    if (!Array.isArray(infoData)) { return; }"
							+ "    const decodedDevices = [];"
							+ "    for (const item of infoData) {"
							+ "      const decoded = decodeJwtPayload(item.deviceInfo);"
							+ "      if (!decoded) { continue; }"
							+ "      if (typeof decoded.digitalId === 'string') {"
							+ "        decoded.digitalId = decodeJwtPayload(decoded.digitalId);"
							+ "      }"
							+ "      if (isValidDevice(decoded)) { decodedDevices.push(decoded); }"
							+ "    }"
							+ "    if (decodedDevices.length > 0) { deviceInfo[port] = decodedDevices; }"
							+ "  } catch (e) {}"
							+ "})).finally(() => {"
							+ "  try {"
							+ "    if (Object.keys(discover).length > 0) {"
							+ "      localStorage.setItem('discover', JSON.stringify(discover));"
							+ "    }"
							+ "    if (Object.keys(deviceInfo).length > 0) {"
							+ "      localStorage.setItem('deviceInfo', JSON.stringify(deviceInfo));"
							+ "      window.dispatchEvent(new Event('storage'));"
							+ "    }"
							+ "  } catch (e) {}"
							+ "  done(true);"
							+ "});");
			rescanActivitySeen = true;
		} catch (Exception ignored) {
			// Browser async discovery is best-effort; widget retry paths still run.
		}
	}

	private void clearBrowserSbiDeviceCache() {
		try {
			((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
					"try {"
							+ "localStorage.removeItem('deviceInfo');"
							+ "localStorage.removeItem('discover');"
							+ "localStorage.removeItem('deviceInfos');"
							+ "} catch (e) {}");
		} catch (Exception ignored) {
			// Best-effort cache reset before rescan.
		}
	}

	private boolean triggerBiometricRescanViaWidget() {
		try {
			Object triggered = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
					"const root = document.querySelector('#secure-biometric-interface-integration');"
							+ "if (!root) { return false; }"
							+ "try {"
							+ "localStorage.removeItem('deviceInfo');"
							+ "localStorage.removeItem('discover');"
							+ "localStorage.removeItem('deviceInfos');"
							+ "} catch (e) {}"
							+ "const candidates = root.querySelectorAll('button, a, [role=\"button\"], span');"
							+ "for (const element of candidates) {"
							+ "  const label = (element.textContent || '').trim().toLowerCase();"
							+ "  if (label.includes('retry') || label.includes('try again')) {"
							+ "    element.click(); return true;"
							+ "  }"
							+ "}"
							+ "const alert = root.querySelector(\"div[role='alert']\");"
							+ "if (alert) {"
							+ "  const tryAgain = Array.from(alert.querySelectorAll('*')).find(el =>"
							+ "    (el.textContent || '').toLowerCase().includes('try again'));"
							+ "  if (tryAgain) { tryAgain.click(); return true; }"
							+ "  alert.click();"
							+ "}"
							+ "const vidTab = root.querySelector('#vid, input[id=\"vid\"], input[name=\"vid\"]');"
							+ "if (vidTab) { vidTab.focus(); vidTab.blur(); return true; }"
							+ "return alert != null;");
			return Boolean.TRUE.equals(triggered);
		} catch (Exception e) {
			return false;
		}
	}

	private WebElement findDisplayedRetryButton() {
		String retryTextXpath = "contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
				+ "'abcdefghijklmnopqrstuvwxyz'),'retry') or contains(translate(normalize-space(.),"
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'try again')";
		List<By> locators = List.of(
				By.xpath("//div[@id='secure-biometric-interface-integration']//button[" + retryTextXpath + "]"),
				By.xpath("//div[@id='secure-biometric-interface-integration']//*[@role='button' and ("
						+ retryTextXpath + ")]"),
				By.xpath("//div[@id='secure-biometric-interface-integration']//a[" + retryTextXpath + "]"),
				By.xpath("//div[@id='secure-biometric-interface-integration']//span[" + retryTextXpath + "]"),
				By.cssSelector("#secure-biometric-interface-integration div[role='alert'] button"),
				By.cssSelector(
						"#secure-biometric-interface-integration button.sbd-cursor-pointer.sbd-block.sbd-w-full"));
		for (By locator : locators) {
			try {
				for (WebElement candidate : driver.findElements(locator)) {
					if (candidate.isDisplayed() && isRetryScanButtonElement(candidate)) {
						return candidate;
					}
				}
			} catch (StaleElementReferenceException ignored) {
				// SBI widget re-renders during scan; retry on next poll.
			}
		}
		return null;
	}

	private boolean isRetryScanButtonElement(WebElement element) {
		try {
			String tagName = element.getTagName();
			if ("input".equalsIgnoreCase(tagName)) {
				return false;
			}
			String className = element.getAttribute("class");
			if (className != null && className.contains("sbd-bg-gradient")) {
				String label = normalizeMessage(safeGetText(element));
				if (!label.contains("retry") && !label.contains("try again")) {
					return false;
				}
			}
			String label = normalizeMessage(safeGetText(element));
			if (label.contains("retry") || label.contains("try again")) {
				return true;
			}
			return className != null && className.contains("sbd-block");
		} catch (StaleElementReferenceException e) {
			return false;
		}
	}

	public void enterBiometricVid(String vid) {
		ensureBiometricVidFieldVisible();
		biometricVidField.clear();
		enterText(biometricVidField, vid, "Entered UIN/VID in biometric field");
		syncBiometricWidgetIfMockMdsRunning();
	}

	public void clearBiometricVidField() {
		ensureBiometricVidFieldVisible();
		biometricVidField.clear();
		biometricVidField.sendKeys(org.openqa.selenium.Keys.TAB);
	}

	public boolean isBiometricScanAndVerifyButtonDisplayed() {
		return findBiometricScanAndVerifyButtons().stream().anyMatch(WebElement::isDisplayed);
	}

	public boolean isBiometricScanAndVerifyButtonEnabled() {
		try {
			List<WebElement> buttons = findBiometricScanAndVerifyButtons();
			for (WebElement button : buttons) {
				try {
					if (button.isDisplayed()) {
						String disabled = button.getAttribute("disabled");
						return disabled == null || "false".equalsIgnoreCase(disabled);
					}
				} catch (StaleElementReferenceException ignored) {
					// SBI widget re-renders during scan; retry on next poll.
				}
			}
		} catch (StaleElementReferenceException ignored) {
			// SBI widget re-renders during scan; retry on next poll.
		}
		return false;
	}

	public boolean isBiometricVidFieldValidationMessageDisplayed() {
		ensureBiometricVidFieldVisible();
		Object message = ((org.openqa.selenium.JavascriptExecutor) driver)
				.executeScript("arguments[0].reportValidity(); return arguments[0].validationMessage;", biometricVidField);
		return message != null && !message.toString().isBlank();
	}

	public void clickMoreWaysToSignInIfVisible() {
		List<WebElement> moreOptions = driver.findElements(By.id("show-more-options"));
		if (!moreOptions.isEmpty() && moreOptions.get(0).isDisplayed()) {
			clickOnElement(moreOptions.get(0), "Clicked on More ways to sign in");
		}
	}

	public boolean isLoginWithBiometricsOptionVisible() {
		return isElementVisible(loginWithBiometricBtn, "Verified Login with Biometrics option is visible");
	}

	public boolean isL0OrUnregisteredDeviceNotAvailable() {
		String containerText = getBiometricContainerText();
		if (containerText.contains("l0")) {
			return false;
		}
		List<WebElement> deviceOptions = driver.findElements(
				By.cssSelector("#secure-biometric-interface-integration .sbd-dropdown_container option, "
						+ "#secure-biometric-interface-integration .sbd-dropdown_container li"));
		for (WebElement option : deviceOptions) {
			if (!option.isDisplayed()) {
				continue;
			}
			String text = normalizeMessage(safeGetText(option));
			if (text.contains("l0") || text.contains("unregistered")) {
				return false;
			}
		}
		return true;
	}

	public boolean waitForBiometricErrorMessageContaining(String... partialMessages) {
		int waitSeconds = getBiometricAuthenticationTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> isBiometricErrorMessageVisible(partialMessages));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public void dismissBiometricErrorBannerIfVisible() {
		List<WebElement> closeButtons = driver.findElements(By.id("error-close-button"));
		for (WebElement closeButton : closeButtons) {
			if (closeButton.isDisplayed()) {
				clickOnElement(closeButton, "Dismissed biometric error banner");
				return;
			}
		}
	}

	public void clickBiometricBackButtonIfVisible() {
		if (isBackButtonDisplayed()) {
			clickOnBackButton();
		}
	}

	public void attemptClickBiometricScanAndVerifyButtonWithoutWait() {
		List<WebElement> buttons = findBiometricScanAndVerifyButtons();
		for (WebElement button : buttons) {
			if (button.isDisplayed()) {
				try {
					button.click();
				} catch (Exception ignored) {
					// Expected when the SBI widget keeps the button disabled.
				}
				return;
			}
		}
	}

	public boolean waitForBiometricDeviceDiscovered() {
		int waitSeconds = getBiometricDeviceDiscoveryTimeoutSeconds();
		long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
		while (System.currentTimeMillis() < deadline) {
			if (isBiometricDeviceDiscovered()) {
				return true;
			}
			if (MockMdsManager.isRunning()) {
				syncBiometricWidgetIfMockMdsRunning();
				injectMockMdsDeviceCacheIfRunning();
				triggerBiometricRescanViaWidget();
				if (findDisplayedRetryButton() != null) {
					clickOnBiometricDeviceScanRetryButton();
				} else {
					triggerBrowserSbiDiscovery();
					injectMockMdsDeviceCacheIfRunning();
				}
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return isBiometricDeviceDiscovered();
	}

	public boolean waitForDeviceNotFoundMessageToClear() {
		int waitSeconds = getBiometricDeviceDiscoveryTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> !isDeviceNotFoundMessageVisible() && isBiometricDeviceDiscovered());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean isDeviceNotFoundMessageDisplayed() {
		return isDeviceNotFoundMessageVisible();
	}

	public void clickBiometricScanAndVerifyButton() {
		syncBiometricWidgetIfMockMdsRunning();
		int waitSeconds = getBiometricAuthenticationTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			WebElement scanButton = wait.until(ExpectedConditions.elementToBeClickable(getBiometricScanAndVerifyButtonLocator()));
			clickOnElement(scanButton, "Clicked biometric scan and verify button");
			return;
		} catch (TimeoutException e) {
			syncBiometricWidgetIfMockMdsRunning();
		}
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		WebElement scanButton = wait.until(ExpectedConditions.elementToBeClickable(getBiometricScanAndVerifyButtonLocator()));
		clickOnElement(scanButton, "Clicked biometric scan and verify button");
	}

	private By getBiometricScanAndVerifyButtonLocator() {
		return By.xpath("//div[@id='secure-biometric-interface-integration']//button[contains("
				+ "translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'scan') "
				+ "or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'verify')]");
	}

	private List<WebElement> findBiometricScanAndVerifyButtons() {
		return driver.findElements(getBiometricScanAndVerifyButtonLocator());
	}

	private boolean isBiometricErrorMessageVisible(String... partialMessages) {
		String bannerText = getVisibleErrorBannerText();
		if (bannerText.isBlank() || partialMessages.length == 0) {
			return false;
		}
		if (partialMessages.length == 1) {
			String partial = partialMessages[0];
			return partial != null && !partial.isBlank()
					&& bannerText.contains(normalizeMessage(partial));
		}
		for (String partial : partialMessages) {
			if (partial != null && !partial.isBlank()
					&& bannerText.contains(normalizeMessage(partial))) {
				return true;
			}
		}
		return false;
	}

	private String getVisibleErrorBannerText() {
		try {
			List<WebElement> banners = driver.findElements(By.id("error-banner-message"));
			for (WebElement banner : banners) {
				if (banner.isDisplayed()) {
					return normalizeMessage(safeGetText(banner));
				}
			}
		} catch (StaleElementReferenceException ignored) {
			return "";
		}
		return "";
	}

	public boolean waitForBiometricAuthenticationSuccess() {
		int waitSeconds = getBiometricAuthenticationTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> isBiometricAuthenticationSuccess());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public String getBiometricAuthenticationFailureDetails() {
		String banner = getVisibleErrorBannerText();
		if (banner.isBlank()) {
			return "";
		}
		return " (UI error: " + banner + ")";
	}

	private int getBiometricScanningWaitSeconds() {
		return parseTimeoutProperty("biometricScanningWaitSeconds", 15);
	}

	private int getBiometricDeviceDiscoveryTimeoutSeconds() {
		return parseTimeoutProperty("biometricDeviceDiscoveryTimeoutSeconds", 30);
	}

	private int getBiometricAuthenticationTimeoutSeconds() {
		return parseTimeoutProperty("biometricAuthenticationTimeoutSeconds", 60);
	}

	private int parseTimeoutProperty(String propertyName, int defaultValue) {
		try {
			String value = EsignetConfigManager.getproperty(propertyName);
			if (value == null || value.isBlank()) {
				return defaultValue;
			}
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private boolean waitForLocalizedTextWithinBiometricContainer(String resourceKey, int timeoutSeconds) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		try {
			wait.until(driver -> isLocalizedTextVisibleWithinBiometricContainer(resourceKey));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private boolean isDeviceNotFoundMessageVisible() {
		if (hasBiometricDeviceDropdown() || isBiometricScanAndVerifyButtonEnabled()) {
			return false;
		}

		String containerText = getBiometricContainerText();
		if (containerText.contains("device not found") && containerText.contains("connectivity")) {
			return true;
		}

		try {
			List<WebElement> alerts = driver.findElements(
					By.cssSelector("#secure-biometric-interface-integration div[role='alert']"));
			for (WebElement alert : alerts) {
				if (alert.isDisplayed()) {
					String alertText = normalizeMessage(safeGetText(alert));
					if (alertText.contains("device not found") && alertText.contains("connectivity")) {
						return true;
					}
				}
			}
		} catch (StaleElementReferenceException ignored) {
			// DOM is still updating while SBI scans for devices; retry on next wait poll.
		}

		String expectedMessage = ResourceBundleLoader.get("errors.no_devices_found_msg");
		if (!expectedMessage.startsWith("!!MISSING_KEY:")
				&& containerText.contains(normalizeMessage(expectedMessage))) {
			return true;
		}

		return false;
	}

	private boolean isTextVisibleWithinBiometricContainer(String normalizedPartialText) {
		if (normalizedPartialText == null || normalizedPartialText.isBlank()) {
			return false;
		}
		return getBiometricContainerText().contains(normalizeMessage(normalizedPartialText));
	}

	private String getBiometricContainerText() {
		try {
			if (!biometricIntegrationContainer.isDisplayed()) {
				return "";
			}
			return normalizeMessage(safeGetText(biometricIntegrationContainer));
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}

	private String safeGetText(WebElement element) {
		try {
			return element.getText();
		} catch (StaleElementReferenceException e) {
			return "";
		}
	}

	private boolean isLocalizedTextVisibleWithinBiometricContainer(String resourceKey) {
		String expectedMessage = ResourceBundleLoader.get(resourceKey);
		if (expectedMessage == null || expectedMessage.startsWith("!!MISSING_KEY:")) {
			return false;
		}
		return isTextVisibleWithinBiometricContainer(normalizeMessage(expectedMessage));
	}

	private boolean isRetryScanButtonVisible() {
		return findDisplayedRetryButton() != null;
	}

	private boolean isBiometricDeviceDiscovered() {
		if (isLocalizedTextVisibleWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY)) {
			return false;
		}
		if (hasBiometricDeviceDropdown()) {
			return true;
		}
		return isBiometricScanAndVerifyButtonDisplayed();
	}

	private boolean isBrowserSbiDeviceCachePopulated() {
		try {
			Object result = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
					"try {"
							+ "const d = JSON.parse(localStorage.getItem('deviceInfo') || '{}');"
							+ "return Object.keys(d).length > 0;"
							+ "} catch (e) { return false; }");
			return Boolean.TRUE.equals(result);
		} catch (Exception e) {
			return false;
		}
	}

	private boolean hasBiometricDeviceDropdown() {
		List<WebElement> deviceDropdowns = driver.findElements(By.cssSelector(
				"#secure-biometric-interface-integration .sbd-dropdown_container, "
						+ "#secure-biometric-interface-integration select"));
		return deviceDropdowns.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasBiometricDeviceSelectionUi() {
		return hasBiometricDeviceDropdown()
				|| (isBiometricScanAndVerifyButtonEnabled() && isBiometricScanAndVerifyButtonDisplayed());
	}

	private boolean isBiometricScanButtonVisible() {
		List<WebElement> scanButtons = driver.findElements(By.xpath(
				"//div[@id='secure-biometric-interface-integration']//button[contains("
						+ "translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'scan') "
						+ "or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'verify')]"));
		return scanButtons.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean isBiometricAuthenticationSuccess() {
		String currentUrl = driver.getCurrentUrl();
		if (currentUrl != null) {
			if (currentUrl.contains("claim-details") || currentUrl.contains("/consent")
					|| currentUrl.contains("userprofile") || currentUrl.contains("code=")) {
				return true;
			}
		}
		return driver.findElements(By.id("continue")).stream().anyMatch(WebElement::isDisplayed);
	}

	private String normalizeMessage(String message) {
		return message == null ? "" : message.replaceAll("\\s+", " ").trim().toLowerCase();
	}

}