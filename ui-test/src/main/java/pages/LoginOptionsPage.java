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
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aventstack.extentreports.Status;

import utils.EsignetConfigManager;
import utils.EsignetUtil;
import utils.ExtentReportManager;
import utils.LanguageUtil;
import utils.LinkAuthUtil;
import utils.MockMdsManager;
import utils.ResourceBundleLoader;

public class LoginOptionsPage extends BasePage {

	private static final Set<String> NON_WALLET_LOGIN_IDS = Set.of(
			"login_with_otp", "login_with_bio", "login_with_pwd", "login_with_pin", "login_with_kbi");

	private static final Logger LOGGER = LoggerFactory.getLogger(LoginOptionsPage.class);

	public LoginOptionsPage(WebDriver driver) {
		super(driver);
	}

	// Verified live (matches ConsentPage.loginTitle): the login-screen title renders as h3#text_heading.
	// "signup-url-button" doesn't exist on this deployment - there's no signup service, and this was
	// never actually a button, just a dead id historically used to read the page's title text.
	@FindBy(id = "text_heading")
	WebElement loginButton;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(id = "login_with_walletname")
	WebElement loginWithInji;

	// Thunder/esignet-go uses #language_selection (radix dropdown or react-select). Classic eSignet
	// used nav button[aria-haspopup='listbox']. Prefer Thunder ids first.
	@FindBy(css = "#language_selection, #language_dropdown, nav button[aria-haspopup='listbox']")
	WebElement languageDropdown;

	@FindBy(xpath = "//button[@role='option' and normalize-space()='हिन्दी']")
	WebElement hindiLanguage;

	// Rewritten against the current "ThunderID" component library used by esignet-go (esqa) - the
	// classic eSignet UI's login_with_* ids no longer exist. Verified by rendering the live login
	// page in a real browser (2026-08-19): the auth-method-selection screen renders acr_otp/
	// acr_password/acr_bio buttons; login_with_pin/login_with_kbi/login_with_walletname weren't
	// observed on that render (client had no PIN/KBI/wallet auth factors registered) and are left
	// as-is pending verification against a client that does.
	@FindBy(id = "acr_otp")
	WebElement loginWithOtpBtn;

	@FindBy(id = "acr_bio")
	WebElement loginWithBiometricBtn;

	@FindBy(id = "login_with_walletname")
	WebElement loginWithInjiBtn;

	@FindBy(id = "acr_password")
	WebElement loginWithPasswordBtn;

	@FindBy(id = "login_with_pin")
	WebElement loginWithPinBtn;

	@FindBy(id = "login_with_kbi")
	WebElement loginWithKbiBtn;

	@FindBy(id = "show-more-options")
	List<WebElement> moreWaysToSignIn;

	// Same ThunderID rewrite as above, verified by rendering the live ID-type/OTP-request screen.
	@FindBy(id = "login_id_mobile")
	WebElement mobileNumberOption;

	@FindBy(id = "login_id_nrc")
	WebElement nrcIdOption;

	// "vid" is now a combined UIN/VID button/field - login_id_uin (Thunder also exposes login_id_vid).
	@FindBy(id = "login_id_uin")
	WebElement vidOption;

	@FindBy(id = "login_id_vid")
	WebElement vidIdTypeOption;

	@FindBy(id = "login_id_email")
	WebElement emailOption;

	@FindBy(id = "back-button")
	WebElement backButton;

	@FindBy(id = "login-header")
	WebElement loginHeader;

	@FindBy(id = "login-subheader")
	WebElement loginSubHeader;

	@FindBy(xpath = "//div[contains(@class,'font-semibold') and contains(@class,'mx-2')]")
	WebElement selectPreferredIdHeader;

	@FindBy(id = "submit_uin")
	WebElement getOtpButton;

	@FindBy(xpath = "//button[@id='login_id_mobile' and contains(@class,'login-id-button--active')]")
	WebElement mobileSelected;

	// Verified live: this is a plain native HTML <select> (no id), not a custom JS dropdown with
	// separately clickable/id'd options - "Otp_login_dropdown_button"/"KHM"/"IND" never existed on
	// this deployment. Its two <option>s carry the country calling codes as their value attribute:
	// value="+91" (India) and value="+855" (Cambodia/KHM) - confirmed via live DOM capture of the
	// mobile-number entry screen. Interact with it via Selenium's Select wrapper, not clickOnElement.
	@FindBy(css = "select.thunderid-affixed-field__prefix-select")
	WebElement prefixNumberField;

	// OTP entry is now 6 separate single-digit boxes (no shared id), not one field - verified by
	// rendering the live OTP screen. Each is aria-labelled "... digit N"; the container carries this
	// class regardless of language.
	@FindBy(css = "input.thunderid-otp-field__input")
	List<WebElement> otpInputFields;

	@FindBy(id = "action_submit_otp")
	WebElement submitOtpButton;

	// Verified live (matches ConsentPage's own attention/consent screen check): the single merged
	// attention/consent screen's real, only interactive element is id="action_allow" - not
	// div.header.my-2, which doesn't exist here.
	@FindBy(id = "action_allow")
	WebElement attentionScreen;

	@FindBy(id = "cancel-button")
	WebElement attentionCancelButton;

	@FindBy(id = "discontinue-button")
	WebElement attentionDiscontinueButton;

	// The ID-type buttons (login_id_uin/login_id_mobile/login_id_email/login_id_nrc) now share a
	// single input field regardless of which type is selected, instead of one field per type.
	@FindBy(id = "username_input")
	WebElement idInputField;

	@FindBy(id = "password_input")
	WebElement passwordInputField;

	@FindBy(id = "password_authenticate")
	WebElement passwordAuthenticateButton;

	// @FindBy(id = "forgot-password-hyperlink")
	// WebElement forgotPasswordLink;

	@FindBy(id = "error-banner-message")
	WebElement invalidIndividualIdErrorMessage;

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
		wait.ignoring(NoSuchElementException.class).ignoring(StaleElementReferenceException.class);
		wait.until(webDriver -> {
			String url = webDriver.getCurrentUrl();
			int hashIndex = url.indexOf('#');
			if (hashIndex >= 0 && hashIndex < url.length() - 10) {
				return true;
			}
			// Thunder/esignet-go stays on /signin (no #<payload>) and renders acr_* chooser buttons
			// plus #language_selection. Check these BEFORE the classic wallet-button lookup: that
			// lookup hits a PageFactory #login_with_walletname proxy which throws NoSuchElement
			// and aborts the rest of this lambda, so the wait never sees the real chooser.
			if (!webDriver.findElements(By.cssSelector("[id^='acr_']")).isEmpty()) {
				return true;
			}
			if (!webDriver.findElements(By.id("username_input")).isEmpty()) {
				return true;
			}
			if (!webDriver.findElements(By.id("language_selection")).isEmpty()) {
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
			} catch (NoSuchElementException | StaleElementReferenceException ignored) {
				// Thunder does not render #login_with_walletname on the acr chooser.
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

	public String clickWalletQrCodeAndCaptureDeepLink() {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript(
				"window.__capturedDeepLink = null;"
						+ "if (!window.__originalWindowOpen) { window.__originalWindowOpen = window.open; }"
						+ "window.open = function(url) { window.__capturedDeepLink = url; return null; };");
		String originalWindow = driver.getWindowHandle();
		WebElement qrButton = waitForElementVisible(By.id("wallet-qr-btn"));
		clickOnElement(qrButton, "Clicked wallet QR code to capture deep link");
		String deepLink = (String) js.executeScript(
				"window.open = window.__originalWindowOpen; return window.__capturedDeepLink;");
		closeExtraBrowserWindows(originalWindow);
		return deepLink;
	}

	private void closeExtraBrowserWindows(String originalWindow) {
		Set<String> handles = driver.getWindowHandles();
		for (String handle : handles) {
			if (!handle.equals(originalWindow)) {
				driver.switchTo().window(handle);
				driver.close();
			}
		}
		driver.switchTo().window(originalWindow);
	}

	public boolean waitForWalletAuthenticateProgressDisplayed() {
		int waitSeconds = Math.max(30, LinkAuthUtil.getMaxUiWaitSeconds());
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(webDriver -> isWalletAuthenticateProgressDisplayed()
					|| (!isWalletQrCodeDisplayed() && hasWalletLinkedSessionIndicators())
					|| (!isWalletQrCodeDisplayed() && isWalletLinkSessionActive()));
			return isWalletAuthenticateProgressDisplayed() || hasWalletLinkedSessionIndicators()
					|| (!isWalletQrCodeDisplayed() && isWalletLinkSessionActive());
		} catch (TimeoutException e) {
			return false;
		}
	}

	public void waitForWalletSessionAfterLinkScan() {
		waitForWalletAuthenticateProgressDisplayed();
	}

	private boolean isWalletLinkSessionActive() {
		return !isWalletQrCodeDisplayed() && !isWalletQrExpiredMessageVisible();
	}

	private boolean hasWalletLinkedSessionIndicators() {
		if (isWalletQrCodeDisplayed()) {
			return false;
		}
		String pageSource = normalizeMessage(driver.getPageSource());
		return pageSource.contains("authenticate") || pageSource.contains("don't refresh")
				|| pageSource.contains("dont refresh") || pageSource.contains("wallet");
	}

	public boolean isWalletAuthenticateProgressDisplayed() {
		if (isWalletQrCodeDisplayed()) {
			return false;
		}
		for (WebElement indicator : driver.findElements(By.cssSelector(".loading-indicator"))) {
			if (!indicator.isDisplayed()) {
				continue;
			}
			String text = normalizeMessage(safeGetText(indicator));
			if (text.contains("authenticate") || text.contains("don't refresh")
					|| text.contains("dont refresh")) {
				return true;
			}
		}
		String expectedMessage = ResourceBundleLoader.get("loadingMsgs.link_auth_waiting");
		if (!expectedMessage.startsWith("!!MISSING_KEY:")) {
			String normalizedExpected = normalizeMessage(expectedMessage);
			for (WebElement indicator : driver.findElements(By.cssSelector(".loading-indicator"))) {
				if (indicator.isDisplayed()
						&& normalizeMessage(safeGetText(indicator)).contains(normalizedExpected.split("\\{\\{")[0])) {
					return true;
				}
			}
		}
		return false;
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

	public boolean isWalletQrCodeImageLoaded() {
		try {
			String src = getWalletQrCodeSrc();
			return src != null && src.startsWith("data:image") && src.length() > 1000;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isWalletQrCodeWithEmbeddedLogo() {
		try {
			String src = getWalletQrCodeSrc();
			return src != null && src.startsWith("data:image") && src.length() > 4000;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isLinkCodeLimitErrorVisible() {
		String bannerText = normalizeMessage(getVisibleErrorBannerText());
		String expected = ResourceBundleLoader.get("errors.link_code_limit_reached");
		if (!expected.startsWith("!!MISSING_KEY:") && bannerText.contains(normalizeMessage(expected))) {
			return true;
		}
		return bannerText.contains("link code") && bannerText.contains("limit");
	}

	public boolean isInvalidQrConfigErrorVisible() {
		String bannerText = normalizeMessage(getVisibleErrorBannerText());
		String expected = ResourceBundleLoader.get("errors.wallet.invalid_qrcode_config");
		if (!expected.startsWith("!!MISSING_KEY:") && bannerText.contains(normalizeMessage(expected))) {
			return true;
		}
		return bannerText.contains("invalid qrcode configuration")
				|| bannerText.contains("invalid qrcode config");
	}

	public boolean waitForRedirectToRelyingPartyWithError(String errorCode) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(45));
		try {
			wait.until(webDriver -> {
				String url = webDriver.getCurrentUrl().toLowerCase();
				return url.contains("error=" + errorCode.toLowerCase())
						|| url.contains("error%3d" + errorCode.toLowerCase());
			});
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public String getCurrentUrlErrorCode() {
		String url = driver.getCurrentUrl();
		int errorIndex = url.indexOf("error=");
		if (errorIndex < 0) {
			errorIndex = url.indexOf("error%3D");
			if (errorIndex >= 0) {
				return extractUrlParam(url.substring(errorIndex + 9));
			}
			return null;
		}
		return extractUrlParam(url.substring(errorIndex + 6));
	}

	private String extractUrlParam(String remainder) {
		int ampIndex = remainder.indexOf('&');
		String value = ampIndex >= 0 ? remainder.substring(0, ampIndex) : remainder;
		try {
			return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			return value;
		}
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
		WebElement dropdown = findLanguageDropdownTrigger();
		if (dropdown == null) {
			throw new TimeoutException("Language dropdown trigger not found on login page");
		}
		clickOnElement(dropdown, "Clicked on language dropdown");
	}

	private WebElement findLanguageDropdownTrigger() {
		List<By> locators = List.of(
				By.id("language_selection"),
				By.cssSelector("#language_dropdown #language_selection"),
				By.cssSelector("#language_dropdown"),
				By.cssSelector("nav button[aria-haspopup='listbox']"),
				By.cssSelector("nav [id='language_selection']"));
		for (By locator : locators) {
			for (WebElement candidate : driver.findElements(locator)) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
		}
		return null;
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

	public boolean isLoginWithKbiDisplayed() {
		return isElementDisplayed(loginWithKbiBtn);
	}

	// KBI can sit behind the "more ways to sign in" expander when the client offers more than a few
	// auth factors; reveal it first so clickOnLoginWithKbi() finds the button.
	public void revealMoreOptionsIfPresent() {
		if (!isElementDisplayed(loginWithKbiBtn) && isMoreWaysToSignInOptionDisplayed()) {
			clickOnElement(moreWaysToSignIn.get(0), "Clicked on more ways to sign in");
		}
	}

	public void clickOnLoginWithKbi() {
		clickOnElement(loginWithKbiBtn, "Clicked on login with KBI");
	}

	public Map<String, WebElement> getAcrToElementMap() {
		Map<String, WebElement> map = new HashMap<>();
		map.put("PWD", firstDisplayedAuthFactor("acr_password", loginWithPasswordBtn));
		map.put("OTP", firstDisplayedAuthFactor("acr_otp", loginWithOtpBtn));
		map.put("BIO", firstDisplayedAuthFactor("acr_bio", loginWithBiometricBtn));
		map.put("WLA", firstDisplayedAuthFactor("acr_wallet", loginWithInjiBtn));
		map.put("PIN", firstDisplayedAuthFactor("acr_pin", loginWithPinBtn));
		map.put("KBI", firstDisplayedAuthFactor("acr_kbi", loginWithKbiBtn));
		return map;
	}

	private WebElement firstDisplayedAuthFactor(String elementId, WebElement fallback) {
		for (WebElement candidate : driver.findElements(By.id(elementId))) {
			try {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			} catch (StaleElementReferenceException ignored) {
				// Chooser re-rendered; fall through to the PageFactory mapping.
			}
		}
		return fallback;
	}

	public void selectLanguage(String language) {
		WebElement langOption = findLanguageOption(language);
		if (langOption == null) {
			throw new TimeoutException("Language option not found: " + language);
		}
		clickOnElement(langOption, "Selected language option: " + language);
		// Selecting a language triggers an async re-fetch/re-render of the whole page (new /flow/meta
		// call for the chosen language, nav bar re-render, etc.) - wait for the dropdown trigger itself
		// to reflect the new selection before returning. Thunder may show the native name ("हिन्दी")
		// or the ISO code ("hi") on the trigger.
		new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout())).until(d -> {
			WebElement trigger = findLanguageDropdownTrigger();
			if (trigger == null) {
				return false;
			}
			String triggerText = normalizeMessage(safeGetText(trigger));
			if (triggerText.contains(normalizeMessage(language))) {
				return true;
			}
			String iso = LanguageUtil.resolveFromBrowserLocale(language);
			return iso != null && !iso.isBlank() && triggerText.equals(normalizeMessage(iso));
		});
	}

	private WebElement findLanguageOption(String language) {
		String literal = toXpathLiteral(language);
		List<By> locators = List.of(
				By.xpath("//button[@role='option' and normalize-space()=" + literal + "]"),
				By.xpath("//*[@role='menuitem' and normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(@class,'langDropdown') and normalize-space()=" + literal + "]"),
				By.xpath("//div[contains(@class,'langDropdown') or contains(@class,'selectedLang')][normalize-space()="
						+ literal + "]"),
				By.xpath("//*[normalize-space()=" + literal + " and (self::button or self::div or @role='option' or @role='menuitem')]"));
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		try {
			return wait.until(d -> {
				for (By locator : locators) {
					for (WebElement candidate : d.findElements(locator)) {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					}
				}
				return null;
			});
		} catch (TimeoutException e) {
			return null;
		}
	}

	/** Opens the dropdown and switches the UI to the given 3-letter language code's display name;
	 *  no-ops if unmapped or already selected (e.g. authorize URL already has ui_locales=en). */
	public void selectLanguageByCode(String languageCode) {
		String displayName = utils.LanguageUtil.getDisplayName(languageCode);
		if (displayName == null || displayName.equals(languageCode)) {
			return;
		}
		waitForAuthorizeFlowReady();
		WebElement trigger = findLanguageDropdownTrigger();
		if (trigger != null) {
			String current = normalizeMessage(safeGetText(trigger));
			if (current.contains(normalizeMessage(displayName))
					|| current.equals(normalizeMessage(LanguageUtil.getIsoLanguageCode(languageCode)))) {
				return;
			}
		} else if (getVisiblePageText().contains(normalizeMessage(displayName))
				|| driver.getCurrentUrl().toLowerCase().contains("ui_locales=en")
						&& "eng".equalsIgnoreCase(languageCode.trim())) {
			// Language control not found but page/URL already matches the requested language.
			return;
		}
		clickOnLanguageDropdown();
		selectLanguage(displayName);
	}

	public boolean isUILanguageChanged(String text) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(Math.max(15, EsignetConfigManager.getTimeout())));
		try {
			wait.until(webDriver -> pageContainsLanguageText(text));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	/**
	 * Thunder's acr-chooser heading is h5#acr_text_heading; the ID-entry screen uses h3#text_heading.
	 * Waiting only on #text_heading times out on the login-options page even when Hindi "लॉगिन" is
	 * clearly rendered in the chooser heading / body.
	 */
	private boolean pageContainsLanguageText(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		for (String elementId : List.of("acr_text_heading", "text_heading", "language_selection")) {
			for (WebElement element : driver.findElements(By.id(elementId))) {
				try {
					if (element.isDisplayed() && safeGetText(element).contains(text)) {
						return true;
					}
				} catch (StaleElementReferenceException ignored) {
					// Language switch re-renders the heading; retry on the next poll.
				}
			}
		}
		try {
			String body = safeGetText(driver.findElement(By.tagName("body")));
			return body.contains(text);
		} catch (Exception e) {
			return false;
		}
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

	public void clickOnMobileNumberOption() {
		selectLoginIdTypeChip("login_id_mobile");
		selectPostfixIfPresent("mobile");
		waitForLoginIdInputReady();
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
		if (!isElementDisplayed(loginWithBiometricBtn) && isMoreWaysToSignInOptionDisplayed()) {
			clickMoreWaysToSignInIfVisible();
		}
		clickOnElement(loginWithBiometricBtn, "Clicked on login with biometrics");
		waitForPageToLoad();
		if (!waitForBiometricScreenReady() && isElementDisplayed(loginWithBiometricBtn)) {
			clickOnElement(loginWithBiometricBtn, "Re-clicked login with biometrics after slow SBI load");
			waitForPageToLoad();
			waitForBiometricScreenReady();
		}
		if (MockMdsManager.isRunning()) {
			triggerBrowserSbiDiscovery();
			injectMockMdsDeviceCacheIfRunning();
		}
	}

	public boolean waitForBiometricScreenReady() {
		int timeoutSeconds = Math.max(getBiometricDeviceDiscoveryTimeoutSeconds(), getBiometricScanningWaitSeconds());
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.pollingEvery(Duration.ofMillis(500))
					.ignoring(StaleElementReferenceException.class)
					.until(webDriver -> isBiometricFlowLandmarkVisible());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean isBiometricFlowLandmarkVisible() {
		if (isBiometricIntegrationContainerVisibleNow()
				|| isBiometricVidOptionVisibleNow()
				|| isBiometricVidTextFieldVisibleNow()
				|| isThunderBiometricIdEntryScreen()
				|| isScanningDevicesMessageVisible()
				|| getVisiblePageText().contains("provide your biometrics")) {
			return true;
		}
		for (WebElement element : driver.findElements(By.cssSelector(
				"#secure-biometric-interface-integration, #sbi_vid, #sbi_uin, #login_id_uin, #login_id_vid"))) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		// Do NOT match authorize URLs that merely contain "biometrics" in acr_values.
		String url = driver.getCurrentUrl();
		if (url == null) {
			return false;
		}
		String lower = url.toLowerCase();
		return lower.contains("/bio") || lower.contains("login-method=bio") || lower.contains("authfactor=bio");
	}

	private boolean isThunderBiometricIdEntryScreen() {
		return findVisibleLoginIdInput() != null
				&& (isElementDisplayed(vidOption) || isElementDisplayed(vidIdTypeOption));
	}

	public void selectBiometricUinVidLoginIdType() {
		waitForBiometricScreenReady();
		if (isBiometricVidTextFieldVisibleNow()) {
			return;
		}
		if (isThunderBiometricIdEntryScreen()) {
			selectUinOrVidLoginIdTypeForOtp(null);
			waitForLoginIdInputReady();
			return;
		}
		for (String elementId : List.of("vid", "login_id_vid", "login_id_uin")) {
			for (WebElement option : driver.findElements(By.id(elementId))) {
				if (option.isDisplayed()) {
					clickOnElement(option, "Selected UIN/VID login ID type on biometric screen");
					if (isBiometricVidTextFieldVisibleNow()) {
						waitForElementVisible(biometricVidField);
					} else {
						waitForLoginIdInputReady();
					}
					return;
				}
			}
		}
		ensureBiometricVidFieldVisible();
	}

	public void triggerBrowserBiometricDiscoveryIfMockMdsRunning() {
		if (!MockMdsManager.isRunning()) {
			return;
		}
		triggerBrowserSbiDiscovery();
		injectMockMdsDeviceCacheIfRunning();
	}

	public void clickOnLoginWithPassword() {
		if (!isElementDisplayed(loginWithPasswordBtn) && isMoreWaysToSignInOptionDisplayed()) {
			clickMoreWaysToSignInIfVisible();
		}
		clickOnElement(loginWithPasswordBtn, "Clicked on login with password");
		waitForPasswordLoginScreenReady();
	}

	public void waitForPasswordLoginScreenReady() {
		new WebDriverWait(driver, Duration.ofSeconds(20))
				.until(webDriver -> isPasswordFieldDisplayedNow() || findVisibleLoginIdInput() != null);
	}

	public boolean isPasswordFieldDisplayed() {
		return isPasswordFieldDisplayedNow();
	}

	private boolean isPasswordFieldDisplayedNow() {
		for (WebElement field : driver.findElements(By.id("password_input"))) {
			if (field.isDisplayed()) {
				return isElementVisible(field, "Verified password field is displayed for authentication");
			}
		}
		for (WebElement field : driver.findElements(By.cssSelector("input[type='password']"))) {
			if (field.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	public void selectUinLoginIdTypeIfAvailable() {
		if (isElementDisplayed(vidOption) && !isLoginIdTypeActive("login_id_uin")) {
			clickOnElement(vidOption, "Selected UIN login ID type");
		} else if (!isUinOrVidLoginIdTypeActive()) {
			selectUinOrVidLoginIdTypeForOtp(null);
		}
		waitForLoginIdInputReady();
	}

	public void selectLoginIdTypeForPassword(String loginIdType) {
		if (loginIdType == null || loginIdType.isBlank()) {
			return;
		}
		String normalized = loginIdType.trim().toLowerCase();
		switch (normalized) {
			case "uin", "uin-password", "mockuin" -> selectUinLoginIdTypeIfAvailable();
			case "email", "emailloginid" -> clickOnEmailOptionButton();
			case "mobile" -> clickOnMobileNumberOption();
			case "nrc" -> clickOnElement(nrcIdOption, "Selected NRC ID login ID type");
			default -> throw new IllegalArgumentException("Unknown password login id type: " + loginIdType);
		}
		waitForLoginIdInputReady();
	}

	public void enterPasswordLoginId(String loginId) {
		waitForLoginIdInputReady();
		WebElement field = findVisibleLoginIdInput();
		setLoginIdFieldValue(field, loginId);
	}

	public void enterPassword(String password) {
		WebElement field = findVisiblePasswordInput();
		clearField(field);
		enterText(field, password, "Entered password in password field");
	}

	private WebElement findVisiblePasswordInput() {
		for (WebElement field : driver.findElements(By.id("password_input"))) {
			if (field.isDisplayed()) {
				return field;
			}
		}
		waitForElementVisible(By.id("password_input"));
		return passwordInputField;
	}

	public void clickOnPasswordLoginButton() {
		syncPasswordLoginFieldsBeforeSubmit();
		solveRecaptchaIfPresent();
		clickOnElement(findPasswordLoginButton(), "Clicked on password login button");
		try {
			waitForPasswordAuthenticationOutcome();
		} catch (TimeoutException e) {
			throw new TimeoutException(
					"Password login did not reach consent, OTP, or a validation error. Page: "
							+ getVisiblePageText(),
					e);
		}
	}

	private WebElement findPasswordLoginButton() {
		for (String elementId : List.of("password_authenticate", "verify_password")) {
			for (WebElement button : driver.findElements(By.id(elementId))) {
				if (button.isDisplayed()) {
					return button;
				}
			}
		}
		return passwordAuthenticateButton;
	}

	private void syncPasswordLoginFieldsBeforeSubmit() {
		for (WebElement field : driver.findElements(By.id("username_input"))) {
			if (!field.isDisplayed()) {
				continue;
			}
			String value = field.getAttribute("value");
			if (value == null || value.isBlank()) {
				return;
			}
			((JavascriptExecutor) driver).executeScript(
					"const el = arguments[0]; const v = arguments[1];"
							+ "el.value = v;"
							+ "el.dispatchEvent(new Event('input', { bubbles: true }));"
							+ "el.dispatchEvent(new Event('change', { bubbles: true }));",
					field, value);
			return;
		}
	}

	public void waitForPasswordAuthenticationOutcome() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		wait.pollingEvery(Duration.ofMillis(300));
		wait.ignoring(StaleElementReferenceException.class);
		// Do not call isAttentionScreenIsDisplayed()/isOtpInputFieldIsDisplayed() here:
		// those use explicitWaitTimeout (~20s) internally, so the first poll never
		// reaches the error-banner/page-text checks before this wait expires.
		wait.until(webDriver -> isAttentionScreenDisplayedNow()
				|| isOtpInputDisplayedNow()
				|| isLoginValidationErrorDisplayedNow());
	}

	private boolean isAttentionScreenDisplayedNow() {
		for (WebElement allow : driver.findElements(By.id("action_allow"))) {
			if (allow.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isOtpInputDisplayedNow() {
		for (WebElement field : driver.findElements(By.cssSelector("input.thunderid-otp-field__input"))) {
			if (field.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isLoginValidationErrorDisplayedNow() {
		if (isLoginIdFieldMarkedInvalid()) {
			return true;
		}
		String banner = getVisibleErrorBannerText();
		if (!banner.isBlank()) {
			return true;
		}
		return pageShowsPasswordAuthError();
	}

	private boolean isLoginIdFieldMarkedInvalid() {
		for (WebElement field : driver.findElements(By.cssSelector(
				"#username_input, input[aria-invalid='true'], [data-invalid], [data-state='error']"))) {
			try {
				if (!field.isDisplayed()) {
					continue;
				}
				String ariaInvalid = field.getAttribute("aria-invalid");
				if ("true".equalsIgnoreCase(ariaInvalid)) {
					return true;
				}
				String dataInvalid = field.getAttribute("data-invalid");
				if (dataInvalid != null && !dataInvalid.isBlank() && !"false".equalsIgnoreCase(dataInvalid)) {
					return true;
				}
			} catch (StaleElementReferenceException ignored) {
				// Field re-rendered while polling.
			}
		}
		return false;
	}

	private boolean pageShowsPasswordAuthError() {
		return isLoginValidationErrorText(getVisiblePageText());
	}

	private boolean isLoginValidationErrorText(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return text.contains("invalid")
				|| text.contains("incorrect")
				|| text.contains("credential")
				|| text.contains("authentication failed")
				|| text.contains("auth failed")
				|| text.contains("wrong password")
				|| text.contains("login failed")
				|| text.contains("please enter valid")
				|| text.contains("enter a valid")
				|| text.contains("enter valid")
				|| text.contains("not a valid")
				|| text.contains("valid individual")
				|| text.contains("valid mobile")
				|| text.contains("valid email")
				|| text.contains("valid vid")
				|| text.contains("valid phone")
				|| text.contains("does not exist")
				|| text.contains("must be between")
				|| text.contains("must be")
				|| text.contains("is required")
				|| text.contains("too short")
				|| text.contains("too long")
				|| text.contains("not allowed");
	}

	public boolean isPasswordLoginButtonEnabled() {
		WebElement button = findPasswordLoginButton();
		if (button == null || !button.isDisplayed()) {
			return false;
		}
		String disabled = button.getAttribute("disabled");
		return disabled == null || disabled.isBlank() || "false".equalsIgnoreCase(disabled);
	}

	public boolean isInvalidCredentialsErrorMessageDisplayed() {
		long deadline = System.currentTimeMillis() + 15_000L;
		while (System.currentTimeMillis() < deadline) {
			if (isLoginValidationErrorDisplayedNow()) {
				return true;
			}
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	// public boolean isForgotPasswordLinkDisplayed() {
	// 	for (WebElement link : driver.findElements(By.id("forgot-password-hyperlink"))) {
	// 		if (link.isDisplayed()) {
	// 			return true;
	// 		}
	// 	}
	// 	return false;
	// }

	public void enterInvalidMobileNumber(String mobileNumber) {
		if (!isMobileNumberSelected()) {
			clickOnMobileNumberOption();
		}
		waitForLoginIdInputReady();
		WebElement field = findVisibleLoginIdInput();
		setLoginIdFieldValue(field, mobileNumber);
		ExtentReportManager.getTest().log(Status.INFO,
				"Entered invalid mobile number into the mobile number field: " + mobileNumber);
	}

	public boolean isGetOtpButtonEnabled() {
		return isButtonEnabled(getOtpButton, "Verified get otp button is enabled");
	}

	public boolean isMobileNumberSelected() {
		boolean selected = isLoginIdTypeActive("login_id_mobile") || isElementDisplayed(mobileSelected);
		if (selected) {
			ExtentReportManager.getTest().log(Status.INFO,
					"Verified mobile number seleted in authentication screen");
		}
		return selected;
	}

	// "Displayed" for a native <select>'s <option> doesn't mean visually rendered (that's the
	// browser/OS's own dropdown chrome, invisible to WebDriver until opened) - it means the option
	// genuinely exists as a selectable choice. Checking via Select.getOptions() is the correct way
	// to interact with a native select in Selenium.
	public boolean isKhmCountryCodePrefixDisplayed() {
		waitForElementVisible(prefixNumberField);
		return new Select(prefixNumberField).getOptions().stream()
				.anyMatch(option -> "+855".equals(option.getAttribute("value")));
	}

	public boolean isIndCountryCodePrefixDisplayed() {
		waitForElementVisible(prefixNumberField);
		return new Select(prefixNumberField).getOptions().stream()
				.anyMatch(option -> "+91".equals(option.getAttribute("value")));
	}

	public void clickOnPrefixNumberFieldButton() {
		waitForElementVisible(prefixNumberField);
		// Native <select>: a real click opens OS dropdown chrome that intercepts later
		// field/button clicks. Options are verified via Select.getOptions() instead.
		ExtentReportManager.getTest().log(Status.INFO, "Clicked on Prefix Number select field");
	}

	public void clickOnIndCountryCodePrefix() {
		waitForElementVisible(prefixNumberField);
		new Select(prefixNumberField).selectByValue("+91");
	}

	public void clickOnKhmCountryCodePrefix() {
		waitForElementVisible(prefixNumberField);
		new Select(prefixNumberField).selectByValue("+855");
	}

	public boolean isOtpInputFieldIsDisplayed() {
		// otpInputFields.isEmpty() has no wait built in - called right after clicking Get OTP, it can
		// race the page transition and see the list still empty even though the OTP screen is about to
		// render. Poll for at least one box to show up first instead of checking once immediately.
		try {
			new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout()))
					.until(d -> !otpInputFields.isEmpty());
		} catch (TimeoutException e) {
			return false;
		}
		return isElementVisible(otpInputFields.get(0), "Verified otp input field is displayed");
	}

	/** Types one OTP digit per box, in order - the OTP field is 6 separate single-character inputs. */
	public void enterOtp(String otp) {
		enterOtpDigits(otpInputFields, otp,
				(field, digit) -> enterText(field, String.valueOf(digit), "Entered OTP digit"));
	}

	public void clickOnSubmitOtpButton() {
		clickOnElement(submitOtpButton, "Clicked on submit OTP button");
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
		selectUinOrVidLoginIdTypeForOtp();
		selectPostfixIfPresent("vid");
		waitForLoginIdInputReady();
	}

	public void selectUinOrVidLoginIdTypeForOtp() {
		selectUinOrVidLoginIdTypeForOtp(null);
	}

	public void selectUinOrVidLoginIdTypeForOtp(String individualId) {
		boolean preferVidChip = individualId == null || individualId.trim().length() >= 16;
		if (preferVidChip) {
			selectLoginIdTypeChip("login_id_vid", "login_id_uin", "vid");
			selectPostfixIfPresent("vid");
		} else {
			selectLoginIdTypeChip("login_id_uin", "login_id_vid", "vid");
			selectPostfixIfPresent("uin");
		}
	}

	private void waitForLoginIdTypeChips() {
		if (isBiometricVidTextFieldVisibleNow()) {
			return;
		}
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20))
					.pollingEvery(Duration.ofMillis(200))
					.ignoring(StaleElementReferenceException.class)
					.until(webDriver -> isAnyLoginIdChipVisible());
		} catch (TimeoutException e) {
			throw new TimeoutException(
					"Login ID type chips did not appear after Login with OTP. URL: " + driver.getCurrentUrl()
							+ " page: " + getVisiblePageText(),
					e);
		}
	}

	private WebElement findVisibleLoginIdChip(String elementId) {
		for (WebElement chip : driver.findElements(By.id(elementId))) {
			try {
				if (chip.isDisplayed()) {
					return chip;
				}
			} catch (StaleElementReferenceException ignored) {
				// Chip re-rendered while scanning.
			}
		}
		return null;
	}

	private void selectLoginIdTypeChip(String... preferredIds) {
		waitForLoginIdTypeChips();
		for (String elementId : preferredIds) {
			WebElement chip = findVisibleLoginIdChip(elementId);
			if (chip == null) {
				continue;
			}
			if (!isLoginIdTypeActive(elementId)) {
				clickOnElement(chip, "Selected " + describeLoginIdChip(elementId) + " login ID type");
				new WebDriverWait(driver, Duration.ofSeconds(10))
						.pollingEvery(Duration.ofMillis(200))
						.ignoring(StaleElementReferenceException.class)
						.until(webDriver -> isLoginIdTypeActive(elementId));
			} else {
				ExtentReportManager.getTest().log(Status.INFO,
						describeLoginIdChip(elementId) + " login ID type already selected");
			}
			return;
		}
		throw new IllegalStateException(
				"None of the login ID type chips were visible: " + String.join(", ", preferredIds)
						+ ". Page: " + getVisiblePageText());
	}

	private String describeLoginIdChip(String elementId) {
		return switch (elementId) {
			case "login_id_vid" -> "VID";
			case "login_id_uin" -> "UIN/VID";
			case "login_id_mobile" -> "mobile number";
			case "login_id_email" -> "email";
			case "login_id_nrc" -> "NRC";
			default -> elementId;
		};
	}

	private void selectPostfixIfPresent(String loginIdType) {
		List<WebElement> postfixSelects = driver.findElements(By.cssSelector(
				"select.thunderid-affixed-field__postfix-select, select.thunderid-affixed-field__suffix-select"));
		for (WebElement selectElement : postfixSelects) {
			if (!selectElement.isDisplayed()) {
				continue;
			}
			Select postfix = new Select(selectElement);
			String match = switch (loginIdType == null ? "" : loginIdType.toLowerCase()) {
				case "vid" -> "id";
				case "uin" -> "uin";
				case "mobile" -> "phone";
				case "email" -> "email";
				case "nrc" -> "nrc";
				default -> loginIdType;
			};
			for (WebElement option : postfix.getOptions()) {
				String value = option.getAttribute("value");
				String text = option.getText();
				String haystack = ((value == null ? "" : value) + " " + (text == null ? "" : text)).toLowerCase();
				if (match != null && !match.isBlank() && haystack.contains(match.toLowerCase())) {
					if (!option.isSelected()) {
						postfix.selectByVisibleText(option.getText());
						ExtentReportManager.getTest().log(Status.INFO,
								"Selected " + loginIdType + " postfix: " + option.getText());
					}
					return;
				}
			}
			return;
		}
	}

	private boolean isLoginIdTypeActive(String elementId) {
		for (WebElement chip : driver.findElements(By.id(elementId))) {
			if (chip.isDisplayed()) {
				String cssClass = chip.getAttribute("class");
				return cssClass != null && cssClass.contains("login-id-button--active");
			}
		}
		return false;
	}

	private boolean isUinOrVidLoginIdTypeActive() {
		return isLoginIdTypeActive("login_id_vid") || isLoginIdTypeActive("login_id_uin")
				|| isLoginIdTypeActive("vid");
	}

	private boolean isAnyLoginIdChipVisible() {
		for (String id : List.of("login_id_mobile", "login_id_vid", "login_id_uin", "login_id_email", "login_id_nrc")) {
			for (WebElement chip : driver.findElements(By.id(id))) {
				if (chip.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void waitForUinOrVidLoginIdTypeActive() {
		if (!isAnyLoginIdChipVisible()) {
			return;
		}
		new WebDriverWait(driver, Duration.ofSeconds(15))
				.pollingEvery(Duration.ofMillis(200))
				.ignoring(StaleElementReferenceException.class)
				.until(webDriver -> isUinOrVidLoginIdTypeActive());
	}

	private void waitForLoginIdInputReady() {
		new WebDriverWait(driver, Duration.ofSeconds(20))
				.until(webDriver -> findVisibleLoginIdInput() != null);
	}

	private WebElement findVisibleLoginIdInput() {
		for (WebElement input : driver.findElements(By.id("username_input"))) {
			if (input.isDisplayed()) {
				return input;
			}
		}
		if (isElementDisplayed(idInputField)) {
			return idInputField;
		}
		return null;
	}

	private void waitForLoginIdFieldValue(WebElement field, String expected) {
		if (expected == null || expected.isBlank()) {
			return;
		}
		String tail = expected.length() > 4 ? expected.substring(expected.length() - 4) : expected;
		new WebDriverWait(driver, Duration.ofSeconds(10))
				.pollingEvery(Duration.ofMillis(200))
				.ignoring(StaleElementReferenceException.class)
				.until(webDriver -> {
					String value = field.getAttribute("value");
					return value != null && (value.contains(expected) || value.endsWith(tail));
				});
	}

	public boolean isInvalidIndividualIdErrorMessageIsDisplayed() {
		long deadline = System.currentTimeMillis() + 15_000L;
		while (System.currentTimeMillis() < deadline) {
			if (isLoginValidationErrorDisplayedNow()) {
				String details = getVisibleErrorBannerText();
				if (details.isBlank()) {
					details = getVisiblePageText();
				}
				ExtentReportManager.getTest().log(Status.INFO,
						"Verified invalid individual id error message is displayed: " + details);
				return true;
			}
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	public boolean waitForOtpAuthenticationDeniedForInfant() {
		long deadline = System.currentTimeMillis() + 30_000L;
		while (System.currentTimeMillis() < deadline) {
			if (isAttentionScreenDisplayedNow()) {
				return false;
			}
			if (!getVisibleErrorBannerText().isBlank()) {
				return true;
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	public String getOtpAuthenticationDenialDetails() {
		String banner = getVisibleErrorBannerText();
		if (!banner.isBlank()) {
			return banner;
		}
		if (isAttentionScreenDisplayedNow()) {
			return "Unexpected navigation to attention screen after infant OTP verify";
		}
		return "No error banner displayed after infant OTP verify";
	}

	public void enterVid(String vid) {
		selectUinOrVidLoginIdTypeForOtp(vid);
		waitForLoginIdInputReady();
		WebElement field = findVisibleLoginIdInput();
		if (field == null) {
			throw new IllegalStateException("Individual ID field was not visible after selecting VID");
		}
		setLoginIdFieldValue(field, vid);
		ExtentReportManager.getTest().log(Status.INFO, "Entered VID into individual ID field: " + vid);
	}

	private void setLoginIdFieldValue(WebElement field, String value) {
		clearField(field);
		if (value == null || value.isBlank()) {
			// Thunder email/mobile inputs trim whitespace; an empty field is the intended state.
			return;
		}
		enterText(field, value, "Entered individual ID");
		if (!loginIdFieldContains(field, value)) {
			enterTextJS(field, value);
		}
		waitForLoginIdFieldValue(field, value);
	}

	private boolean loginIdFieldContains(WebElement field, String expected) {
		String actual = field.getAttribute("value");
		if (actual == null || actual.isBlank()) {
			return false;
		}
		String tail = expected.length() > 4 ? expected.substring(expected.length() - 4) : expected;
		return actual.contains(expected) || actual.endsWith(tail);
	}

	public void clickOnEmailOptionButton() {
		selectLoginIdTypeChip("login_id_email");
		selectPostfixIfPresent("email");
		waitForLoginIdInputReady();
	}

	public void enterEmail(String email) {
		clickOnEmailOptionButton();
		WebElement field = findVisibleLoginIdInput();
		if (field == null) {
			throw new IllegalStateException("Individual ID field was not visible after selecting email");
		}
		setLoginIdFieldValue(field, email);
		ExtentReportManager.getTest().log(Status.INFO, "Entered email into individual ID field: " + email);
	}

	public boolean isBiometricIntegrationContainerDisplayed() {
		return isElementVisible(biometricIntegrationContainer,
				"Verified secure biometric interface integration container is displayed");
	}

	public boolean isBiometricScreenActive() {
		return isBiometricIntegrationContainerVisibleNow()
				|| isThunderBiometricIdEntryScreen()
				|| isBiometricVidOptionVisibleNow()
				|| isBiometricVidTextFieldVisibleNow()
				|| isBiometricDeviceDiscovered()
				|| getVisiblePageText().contains("provide your biometrics");
	}

	private boolean isBiometricIntegrationContainerVisibleNow() {
		List<WebElement> containers = driver.findElements(By.id("secure-biometric-interface-integration"));
		return !containers.isEmpty() && containers.get(0).isDisplayed();
	}

	private boolean isBiometricVidOptionVisibleNow() {
		for (String elementId : List.of("vid", "login_id_uin", "login_id_vid")) {
			for (WebElement option : driver.findElements(By.id(elementId))) {
				if (option.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isBiometricVidTextFieldVisibleNow() {
		for (String fieldId : List.of("sbi_vid", "sbi_uin")) {
			List<WebElement> fields = driver.findElements(By.id(fieldId));
			if (!fields.isEmpty() && fields.get(0).isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement findBiometricSbiInputField() {
		for (String fieldId : List.of("sbi_vid", "sbi_uin")) {
			for (WebElement field : driver.findElements(By.id(fieldId))) {
				if (field.isDisplayed()) {
					return field;
				}
			}
		}
		return null;
	}

	/**
	 * Thunder biometric flow: select UIN/VID (or mobile/email) → enter value → Continue → scanning page.
	 * Classic SBI may scan on the same page after ID entry without a Continue button.
	 */
	public void ensureBiometricScanPrerequisiteIdEntered() {
		if (isScanningDevicesMessageVisible() || isDeviceNotFoundMessageVisible()
				|| isBiometricDeviceDiscovered()) {
			return;
		}
		selectBiometricUinVidLoginIdType();
		WebElement sbiField = findBiometricSbiInputField();
		if (sbiField != null) {
			String existing = sbiField.getAttribute("value");
			if (existing == null || existing.isBlank()) {
				String uin = resolvePrerequisiteUinForBiometric();
				if (uin != null) {
					enterBiometricVid(uin);
				}
			} else {
				sbiField.sendKeys(org.openqa.selenium.Keys.TAB);
			}
			waitForBiometricWidgetAfterIdEntry();
			return;
		}
		WebElement loginIdField = findVisibleLoginIdInput();
		if (loginIdField != null) {
			String existing = loginIdField.getAttribute("value");
			if (existing == null || existing.isBlank()) {
				String uin = resolvePrerequisiteUinForBiometric();
				if (uin != null) {
					enterBiometricVid(uin);
				}
			}
			clickContinueOnBiometricLoginIdScreen();
			waitForBiometricWidgetAfterIdEntry();
		}
	}

	private String resolvePrerequisiteUinForBiometric() {
		String uin = EsignetUtil.getPrerequisiteUinForBiometricLogin();
		if (uin == null || uin.isBlank()) {
			uin = EsignetConfigManager.getproperty("mockUin");
		}
		if (uin == null || uin.isBlank()) {
			uin = EsignetConfigManager.getproperty("uin");
		}
		return (uin == null || uin.isBlank()) ? null : uin.trim();
	}

	/**
	 * Thunder ID-entry screen uses an orange Continue button (same role as Get OTP on OTP flow).
	 * No-op on classic SBI (sbi_vid present) or when already on the scanning page.
	 */
	public void clickContinueOnBiometricLoginIdScreen() {
		if (isScanningDevicesMessageVisible() || isDeviceNotFoundMessageVisible()
				|| isBiometricDeviceDiscovered()) {
			return;
		}
		if (findBiometricSbiInputField() != null) {
			return;
		}
		WebElement continueButton = findBiometricLoginIdContinueButton();
		if (continueButton == null) {
			throw new TimeoutException("Continue button not found on biometric login ID screen");
		}
		clickOnElement(continueButton, "Clicked Continue on biometric login ID screen");
		waitForBiometricWidgetAfterIdEntry();
	}

	private WebElement findBiometricLoginIdContinueButton() {
		List<By> locators = List.of(
				By.id("submit_uin"),
				By.id("continue"),
				By.id("form-submit-button"),
				By.xpath("//button[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'continue')]"),
				By.cssSelector("button[type='submit']"));
		for (By locator : locators) {
			try {
				for (WebElement candidate : driver.findElements(locator)) {
					if (candidate.isDisplayed() && candidate.isEnabled()) {
						return candidate;
					}
				}
			} catch (StaleElementReferenceException ignored) {
				// Retry next locator.
			}
		}
		return null;
	}

	private void waitForBiometricWidgetAfterIdEntry() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(getBiometricScanningWaitSeconds()))
					.pollingEvery(Duration.ofMillis(500))
					.until(webDriver -> isScanningDevicesMessageVisible()
							|| isDeviceNotFoundMessageVisible()
							|| isBiometricDeviceDiscovered()
							|| isBiometricIntegrationContainerVisibleNow());
		} catch (TimeoutException ignored) {
			// Caller assertions surface missing scan state.
		}
	}

	public boolean isBiometricVidOptionDisplayed() {
		waitForBiometricScreenReady();
		if (isBiometricVidOptionVisibleNow() || isThunderBiometricIdEntryScreen()) {
			return true;
		}
		return isElementVisible(vidOption, "Verified UIN/VID option is displayed on biometric screen");
	}

	public void clickOnBiometricVidOptionButton() {
		waitForBiometricScreenReady();
		if (isScanningDevicesMessageVisible() || isDeviceNotFoundMessageVisible()
				|| isBiometricDeviceDiscovered()) {
			return;
		}
		if (isBiometricVidTextFieldVisibleNow() || findVisibleLoginIdInput() != null) {
			selectBiometricUinVidLoginIdType();
			return;
		}
		for (String elementId : List.of("vid", "login_id_vid", "login_id_uin")) {
			for (WebElement option : driver.findElements(By.id(elementId))) {
				if (option.isDisplayed()) {
					clickOnElement(option, "Clicked on UIN/VID option on biometric screen");
					return;
				}
			}
		}
		selectBiometricUinVidLoginIdType();
	}

	/**
	 * SBI widget re-renders after Mock MDS retry can hide the UIN/VID input until the tab is selected again.
	 */
	public void ensureBiometricVidFieldVisible() {
		if (isScanningDevicesMessageVisible() || isDeviceNotFoundMessageVisible()
				|| isBiometricDeviceDiscovered()) {
			return;
		}
		if (isBiometricVidTextFieldVisibleNow()) {
			return;
		}
		if (isThunderBiometricIdEntryScreen()) {
			selectUinOrVidLoginIdTypeForOtp(null);
			waitForLoginIdInputReady();
			return;
		}
		if (isBiometricVidOptionVisibleNow()) {
			clickOnBiometricVidOptionButton();
		}
		if (isBiometricVidTextFieldVisibleNow()) {
			waitForElementVisible(biometricVidField);
		}
	}

	public boolean isBiometricVidTextFieldDisplayed() {
		WebElement sbiField = findBiometricSbiInputField();
		if (sbiField != null) {
			return isElementVisible(sbiField, "Verified SBI UIN/VID text field is displayed on biometric screen");
		}
		WebElement field = findVisibleLoginIdInput();
		return field != null && field.isDisplayed();
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
				|| isTextVisibleWithinBiometricContainer("scanning devices")
				|| getVisiblePageText().contains("scanning devices");
	}

	public boolean isScanningDevicesMessageDisplayed() {
		if (waitForLocalizedTextWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY, getBiometricScanningWaitSeconds())) {
			return true;
		}
		try {
			new WebDriverWait(driver, Duration.ofSeconds(getBiometricScanningWaitSeconds()))
					.pollingEvery(Duration.ofMillis(500))
					.until(webDriver -> getVisiblePageText().contains("scanning devices"));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean isRetryScanButtonNotDisplayedWhileScanning() {
		if (!isScanningDevicesMessageVisible()
				&& !waitForLocalizedTextWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY, 5)) {
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
			LOGGER.warn("Device-not-found state was not detected. SBI text: {}", getSbiVisibleText());
			return false;
		}
	}

	private static final By ICON_RETRY_BUTTON_SELECTOR = By.cssSelector(
			"#secure-biometric-interface-integration button[type='button'].sbd-cursor-pointer.sbd-ml-1, "
					+ "#secure-biometric-interface-integration div.sbd-dropdown_container + button[type='button'], "
					+ "#secure-biometric-interface-integration div.sbd-flex button[type='button'].sbd-cursor-pointer");

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
		if (!MockMdsManager.isRunning() || !isBiometricScanningOrDiscoveredScreen()) {
			return;
		}
		if (isBiometricDeviceDiscovered()) {
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
		if (!isBiometricDeviceDiscovered() && isDeviceNotFoundMessageVisible()) {
			reenterBiometricLoginAfterMockMdsStart();
		}
	}

	private boolean isBiometricScanningOrDiscoveredScreen() {
		return isBiometricDeviceDiscovered()
				|| isScanningDevicesMessageVisible()
				|| isDeviceNotFoundMessageVisible()
				|| isBiometricIntegrationContainerVisibleNow()
				|| getVisiblePageText().contains("provide your biometrics");
	}

	/**
	 * When Mock MDS starts after the widget already scanned with no device, going back and re-opening
	 * biometric login forces a fresh SBI discovery pass (more reliable than retry alone).
	 */
	public void reenterBiometricLoginAfterMockMdsStart() {
		if (!MockMdsManager.isRunning()) {
			return;
		}
		if (isBiometricDeviceDiscovered()) {
			return;
		}
		String pageText = getVisiblePageText();
		if (pageText.contains("provide your biometrics") && !isDeviceNotFoundMessageVisible()) {
			triggerBrowserSbiDiscovery();
			injectMockMdsDeviceCacheIfRunning();
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
			WebDriverWait loginOptionsWait = new WebDriverWait(driver, Duration.ofSeconds(15));
			loginOptionsWait.until(webDriver -> isElementDisplayed(loginWithBiometricBtn));
			clickOnElement(loginWithBiometricBtn, "Re-opened Login with Biometrics after Mock MDS start");
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(getBiometricScanningWaitSeconds()));
			wait.until(driver -> isBiometricFlowLandmarkVisible());
			selectBiometricUinVidLoginIdType();
			ensureBiometricScanPrerequisiteIdEntered();
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
		if (!triggerBiometricRescanViaWidget()) {
			selectBiometricUinVidLoginIdType();
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
			String clearCache = MockMdsManager.isRunning()
					? ""
					: "try { localStorage.removeItem('deviceInfo'); localStorage.removeItem('discover');"
							+ " localStorage.removeItem('deviceInfos'); } catch (e) {}";
			Object triggered = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
					"const root = document.querySelector('#secure-biometric-interface-integration');"
							+ "if (!root) { return false; }"
							+ clearCache
							+ "const candidates = root.querySelectorAll('button, a, [role=\"button\"], span');"
							+ "for (const element of candidates) {"
							+ "  const label = (element.textContent || '').trim().toLowerCase();"
							+ "  const aria = (element.getAttribute('aria-label') || element.getAttribute('title') || '').toLowerCase();"
							+ "  if (label.includes('retry') || label.includes('try again') || label.includes('refresh')"
							+ "      || aria.includes('retry') || aria.includes('refresh')) {"
							+ "    element.click(); return true;"
							+ "  }"
							+ "}"
							+ "const dropdown = root.querySelector('.sbd-dropdown_container, [class*=\"dropdown\"]');"
							+ "if (dropdown && dropdown.nextElementSibling && dropdown.nextElementSibling.tagName === 'BUTTON') {"
							+ "  dropdown.nextElementSibling.click(); return true;"
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
				ICON_RETRY_BUTTON_SELECTOR,
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
			String label = normalizeMessage(safeGetText(element));
			if (className != null && className.contains("sbd-bg-gradient")
					&& !label.contains("retry") && !label.contains("try again") && !label.contains("refresh")) {
				return false;
			}
			if (label.contains("retry") || label.contains("try again") || label.contains("refresh")) {
				return true;
			}
			String aria = normalizeMessage(element.getAttribute("aria-label"));
			String title = normalizeMessage(element.getAttribute("title"));
			if (aria.contains("retry") || aria.contains("refresh") || title.contains("retry")
					|| title.contains("refresh")) {
				return true;
			}
			// Thunder SBI: icon-only circular refresh next to the empty "Select a device" dropdown.
			if ("button".equalsIgnoreCase(tagName) && (label.isBlank() || label.length() <= 2)) {
				if (isAdjacentToDeviceDropdown(element)) {
					return true;
				}
				if (className != null && className.contains("sbd-cursor-pointer")
						&& !className.contains("sbd-bg-gradient")) {
					return true;
				}
			}
			return className != null && className.contains("sbd-block");
		} catch (StaleElementReferenceException e) {
			return false;
		}
	}

	private boolean isAdjacentToDeviceDropdown(WebElement element) {
		try {
			Object previous = ((JavascriptExecutor) driver)
					.executeScript("return arguments[0].previousElementSibling;", element);
			if (!(previous instanceof WebElement previousElement)) {
				return false;
			}
			String previousClass = previousElement.getAttribute("class");
			String previousId = previousElement.getAttribute("id");
			return (previousClass != null && previousClass.toLowerCase().contains("dropdown"))
					|| "sbi-device".equals(previousId);
		} catch (Exception e) {
			return false;
		}
	}

	public void enterBiometricVid(String vid) {
		ensureBiometricVidFieldVisible();
		WebElement sbiField = findBiometricSbiInputField();
		if (sbiField != null) {
			clearField(sbiField);
			enterText(sbiField, vid, "Entered UIN/VID in biometric field");
			syncBiometricWidgetIfMockMdsRunning();
		} else {
			WebElement field = findVisibleLoginIdInput();
			setLoginIdFieldValue(field, vid);
			// Thunder: scanning starts only after Continue - do not sync SBI on the ID-entry screen.
		}
	}

	public void clearBiometricVidField() {
		ensureBiometricVidFieldVisible();
		WebElement sbiField = findBiometricSbiInputField();
		if (sbiField != null) {
			clearField(sbiField);
			sbiField.sendKeys(org.openqa.selenium.Keys.TAB);
		} else {
			WebElement field = findVisibleLoginIdInput();
			if (field != null) {
				clearField(field);
				field.sendKeys(org.openqa.selenium.Keys.TAB);
			}
		}
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
		WebElement scanButton;
		try {
			scanButton = wait.until(driver -> findDisplayedBiometricScanAndVerifyButton());
		} catch (TimeoutException e) {
			syncBiometricWidgetIfMockMdsRunning();
			wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
			scanButton = wait.until(driver -> findDisplayedBiometricScanAndVerifyButton());
		}
		clickBiometricScanAndVerifyElement(scanButton);
	}

	private void clickBiometricScanAndVerifyElement(WebElement scanButton) {
		try {
			clickOnElement(scanButton, "Clicked biometric scan and verify button");
		} catch (Exception e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", scanButton);
			ExtentReportManager.getTest().log(Status.INFO, "Clicked biometric scan and verify button via JavaScript");
		}
	}

	private List<By> getBiometricScanAndVerifyButtonLocators() {
		String scanAndVerify = "contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
				+ "'abcdefghijklmnopqrstuvwxyz'),'scan') and contains(translate(normalize-space(.),"
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'verify')";
		return List.of(
				By.xpath("//div[@id='secure-biometric-interface-integration']//button[" + scanAndVerify + "]"),
				By.xpath("//button[" + scanAndVerify + "]"),
				By.xpath("//*[@role='button' and (" + scanAndVerify + ")]"),
				By.xpath("//div[@id='secure-biometric-interface-integration']//*[" + scanAndVerify + "]"));
	}

	private List<WebElement> findBiometricScanAndVerifyButtons() {
		List<WebElement> matches = new ArrayList<>();
		for (By locator : getBiometricScanAndVerifyButtonLocators()) {
			matches.addAll(driver.findElements(locator));
		}
		WebElement jsMatch = findScanAndVerifyButtonViaJs();
		if (jsMatch != null) {
			matches.add(jsMatch);
		}
		return matches;
	}

	private WebElement findDisplayedBiometricScanAndVerifyButton() {
		for (WebElement button : findBiometricScanAndVerifyButtons()) {
			try {
				if (!button.isDisplayed()) {
					continue;
				}
				String text = normalizeMessage(safeGetText(button));
				if (text.length() > 80) {
					continue;
				}
				if ((text.contains("scan") && text.contains("verify")) || text.contains("scan & verify")) {
					return button;
				}
			} catch (StaleElementReferenceException ignored) {
				// Widget re-renders while devices are discovered.
			}
		}
		return findScanAndVerifyButtonViaJs();
	}

	private WebElement findScanAndVerifyButtonViaJs() {
		try {
			Object result = ((JavascriptExecutor) driver).executeScript(
					"const labels = ['scan and verify', 'scan & verify'];"
							+ "const search = (root) => {"
							+ "  let best = null;"
							+ "  let bestLen = Number.POSITIVE_INFINITY;"
							+ "  const elements = root.querySelectorAll('button, [role=\"button\"], a, div, span');"
							+ "  for (const el of elements) {"
							+ "    const text = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();"
							+ "    if (!text || text.length > 40) { continue; }"
							+ "    if (labels.some((label) => text === label || text.includes(label))) {"
							+ "      if (text.length < bestLen) { best = el; bestLen = text.length; }"
							+ "    }"
							+ "    if (el.shadowRoot) {"
							+ "      const nested = search(el.shadowRoot);"
							+ "      if (nested) { return nested; }"
							+ "    }"
							+ "  }"
							+ "  return best;"
							+ "};"
							+ "return search(document);");
			if (result instanceof WebElement) {
				return (WebElement) result;
			}
		} catch (Exception ignored) {
			// Best-effort lookup when the SBI widget is not a native <button>.
		}
		return null;
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
			List<By> locators = List.of(
					By.id("error-banner-message"),
					By.id("error-banner"),
					By.cssSelector("[role='alert']"),
					By.cssSelector("[aria-live='assertive']"),
					By.cssSelector("[data-slot='form-message']"),
					By.cssSelector("[class*='field__error']"),
					By.cssSelector("[class*='form-error']"),
					By.cssSelector("[class*='error-message']"),
					By.cssSelector("[class*='error-banner']"));
			for (By locator : locators) {
				for (WebElement banner : driver.findElements(locator)) {
					if (!banner.isDisplayed()) {
						continue;
					}
					String text = normalizeMessage(safeGetText(banner));
					if (!text.isBlank()) {
						return text;
					}
				}
			}
			for (WebElement field : driver.findElements(By.cssSelector("[aria-invalid='true']"))) {
				if (!field.isDisplayed()) {
					continue;
				}
				String describedBy = field.getAttribute("aria-describedby");
				if (describedBy == null || describedBy.isBlank()) {
					continue;
				}
				for (String id : describedBy.split("\\s+")) {
					for (WebElement msg : driver.findElements(By.id(id))) {
						if (msg.isDisplayed()) {
							String text = normalizeMessage(safeGetText(msg));
							if (!text.isBlank()) {
								return text;
							}
						}
					}
				}
			}
		} catch (StaleElementReferenceException ignored) {
			return "";
		}
		return "";
	}

	public boolean waitForBiometricScanCompletedWithoutDevice() {
		logBiometricOutcome("Waiting for biometric device scan to complete with no device found");
		boolean completed = waitForDeviceNotFoundMessageDisplayed() && !isBiometricDeviceDiscovered();
		if (completed) {
			logBiometricOutcome("Biometric device scan completed: device not found");
			return true;
		}
		logBiometricOutcome("Biometric device scan did not complete with no device found."
				+ describeBiometricEndState());
		return false;
	}

	public boolean waitForBiometricScanCompletedWithDevice() {
		logBiometricOutcome("Waiting for biometric device scan to complete with a discovered device");
		if (MockMdsManager.isRunning()) {
			syncBiometricWidgetIfMockMdsRunning();
		}
		boolean discovered = waitForBiometricDeviceDiscovered();
		boolean scanReady = discovered && waitForBiometricScanAndVerifyButtonDisplayed();
		if (scanReady) {
			logBiometricOutcome("Biometric device scan completed: device discovered, Scan and Verify is available");
			return true;
		}
		logBiometricOutcome("Biometric device scan did not complete with a discovered device."
				+ describeBiometricEndState());
		return discovered && isBiometricScanAndVerifyButtonDisplayed();
	}

	public boolean waitForBiometricScanAndVerifyButtonDisplayed() {
		int waitSeconds = getBiometricDeviceDiscoveryTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(webDriver -> findDisplayedBiometricScanAndVerifyButton() != null);
			return true;
		} catch (TimeoutException e) {
			return isBiometricScanAndVerifyButtonDisplayed();
		}
	}

	public String describeBiometricEndState() {
		StringBuilder details = new StringBuilder();
		details.append(" URL=").append(driver.getCurrentUrl());
		details.append(" scanning=").append(isScanningDevicesMessageVisible());
		details.append(" deviceNotFound=").append(isDeviceNotFoundMessageVisible());
		details.append(" deviceDiscovered=").append(isBiometricDeviceDiscovered());
		details.append(" scanAndVerify=").append(isBiometricScanAndVerifyButtonDisplayed());
		details.append(" attention=").append(isAttentionScreenDisplayedNow());
		String banner = getVisibleErrorBannerText();
		if (!banner.isBlank()) {
			details.append(" error=").append(banner);
		}
		return details.toString();
	}

	private void logBiometricOutcome(String message) {
		LOGGER.info(message);
		ExtentReportManager.getTest().log(Status.INFO, message);
	}

	public boolean waitForBiometricAuthenticationSuccess() {
		logBiometricOutcome("Waiting for biometric authentication to complete");
		int waitSeconds = getBiometricAuthenticationTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(webDriver -> isBiometricAuthenticationSuccess());
			logBiometricOutcome("Biometric authentication completed." + describeBiometricEndState());
			return true;
		} catch (TimeoutException e) {
			logBiometricOutcome("Biometric authentication did not complete." + describeBiometricEndState());
			return false;
		}
	}

	public String getBiometricAuthenticationFailureDetails() {
		String banner = getVisibleErrorBannerText();
		if (banner.isBlank()) {
			return describeBiometricEndState();
		}
		return " (UI error: " + banner + ")" + describeBiometricEndState();
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
		if (hasDiscoveredBiometricDevice() || isBiometricScanAndVerifyButtonEnabled()) {
			return false;
		}

		String combined = getSbiVisibleText();
		if (isScanningDevicesMessageVisible() && !containsDeviceNotFoundCopy(combined)) {
			return false;
		}
		if (containsDeviceNotFoundCopy(combined)) {
			return true;
		}

		try {
			List<WebElement> alerts = driver.findElements(
					By.cssSelector("#secure-biometric-interface-integration div[role='alert'], div[role='alert']"));
			for (WebElement alert : alerts) {
				if (alert.isDisplayed() && containsDeviceNotFoundCopy(normalizeMessage(safeGetText(alert)))) {
					return true;
				}
			}
		} catch (StaleElementReferenceException ignored) {
			// DOM is still updating while SBI scans for devices; retry on next wait poll.
		}

		String expectedMessage = ResourceBundleLoader.get("errors.no_devices_found_msg");
		if (!expectedMessage.startsWith("!!MISSING_KEY:")
				&& combined.contains(normalizeMessage(expectedMessage))) {
			return true;
		}
		return false;
	}

	private boolean containsDeviceNotFoundCopy(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return text.contains("device not found")
				|| text.contains("no devices found")
				|| text.contains("no device found")
				|| text.contains("no options")
				|| (text.contains("connectivity") && (text.contains("retry") || text.contains("try again")));
	}

	private String getSbiVisibleText() {
		return (getBiometricContainerText() + " " + getVisiblePageText()).trim();
	}

	private String getVisiblePageText() {
		try {
			return normalizeMessage(safeGetText(driver.findElement(By.tagName("body"))));
		} catch (Exception e) {
			return "";
		}
	}

	private boolean isTextVisibleWithinBiometricContainer(String normalizedPartialText) {
		if (normalizedPartialText == null || normalizedPartialText.isBlank()) {
			return false;
		}
		return getBiometricContainerText().contains(normalizeMessage(normalizedPartialText));
	}

	private String getBiometricContainerText() {
		try {
			List<WebElement> containers = driver.findElements(By.id("secure-biometric-interface-integration"));
			if (containers.isEmpty()) {
				return "";
			}
			WebElement container = containers.get(0);
			if (!container.isDisplayed()) {
				return "";
			}
			return normalizeMessage(safeGetText(container));
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
		if (containsDeviceNotFoundCopy(getSbiVisibleText())) {
			return false;
		}
		if (findDisplayedBiometricScanAndVerifyButton() != null) {
			return true;
		}
		return hasDiscoveredBiometricDevice();
	}

	/** Public wrapper for step definitions that need discovery status after Mock MDS retry. */
	public boolean isBiometricDeviceDiscoveredPublic() {
		return isBiometricDeviceDiscovered();
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

	/**
	 * True only when the SBI widget lists a real device. Thunder always renders the
	 * "Select a device" dropdown with "No Options" when discovery fails — that is not a device.
	 */
	private boolean hasDiscoveredBiometricDevice() {
		String combined = getSbiVisibleText();
		if (containsDeviceNotFoundCopy(combined)) {
			return false;
		}
		if (combined.contains("mosip-single") || combined.contains("mosip-face")
				|| combined.contains("mosip-iris") || combined.contains("mosip-finger")
				|| combined.contains("mosip-double")) {
			return true;
		}
		List<By> optionLocators = List.of(
				By.cssSelector("#secure-biometric-interface-integration .sbd-dropdown_container option, "
						+ "#secure-biometric-interface-integration .sbd-dropdown_container li, "
						+ "#secure-biometric-interface-integration [class*='option']"),
				By.xpath("//div[@id='secure-biometric-interface-integration']//*[contains(translate(normalize-space(.),"
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'mosip-')]"));
		for (By locator : optionLocators) {
			for (WebElement element : driver.findElements(locator)) {
				try {
					if (!element.isDisplayed()) {
						continue;
					}
					if (isRealDiscoveredDeviceLabel(normalizeMessage(safeGetText(element)))) {
						return true;
					}
				} catch (StaleElementReferenceException ignored) {
					// Widget re-renders while devices are discovered.
				}
			}
		}
		return false;
	}

	private boolean isRealDiscoveredDeviceLabel(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		if (text.contains("select a device") || text.contains("no options") || text.contains("no device")
				|| text.contains("scanning")) {
			return false;
		}
		return text.contains("mosip") || text.contains("finger") || text.contains("face") || text.contains("iris")
				|| text.contains("slap") || text.contains("l1");
	}

	public boolean isBiometricLoginCompleted() {
		boolean completed = isBiometricAuthenticationSuccess();
		if (completed) {
			logBiometricOutcome("Biometric login completed." + describeBiometricEndState());
		} else {
			logBiometricOutcome("Biometric login is not complete." + describeBiometricEndState());
		}
		return completed;
	}

	private boolean isBiometricAuthenticationSuccess() {
		if (isAttentionScreenDisplayedNow()) {
			return true;
		}
		String currentUrl = driver.getCurrentUrl();
		if (currentUrl != null) {
			if (currentUrl.contains("claim-details") || currentUrl.contains("/consent")
					|| currentUrl.contains("userprofile") || currentUrl.contains("code=")) {
				return true;
			}
		}
		return false;
	}

	private String normalizeMessage(String message) {
		return message == null ? "" : message.replaceAll("\\s+", " ").trim().toLowerCase();
	}

}