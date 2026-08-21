package pages;

import base.BasePage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
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

import utils.EsignetConfigManager;
import utils.ResourceBundleLoader;

public class LoginOptionsPage extends BasePage {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoginOptionsPage.class);

	public LoginOptionsPage(WebDriver driver) {
		super(driver);
	}

	@FindBy(id = "text_heading")
	WebElement loginButton;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(id = "login_with_walletname")
	WebElement loginWithInji;

	@FindBy(css = "nav button[aria-haspopup='listbox']")
	WebElement languageDropdown;

	@FindBy(xpath = "//button[@role='option' and normalize-space()='हिन्दी']")
	WebElement hindiLanguage;

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

	@FindBy(id = "login_id_mobile")
	WebElement mobileNumberOption;

	@FindBy(id = "login_id_nrc")
	WebElement nrcIdOption;

	@FindBy(id = "login_id_uin")
	WebElement vidOption;

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

	@FindBy(css = "select.thunderid-affixed-field__prefix-select")
	WebElement prefixNumberField;

	@FindBy(css = "input.thunderid-otp-field__input")
	List<WebElement> otpInputFields;

	@FindBy(id = "action_submit_otp")
	WebElement submitOtpButton;

	@FindBy(id = "action_allow")
	WebElement attentionScreen;

	@FindBy(id = "cancel-button")
	WebElement attentionCancelButton;

	@FindBy(id = "discontinue-button")
	WebElement attentionDiscontinueButton;

	// ID-type login buttons (UIN/VID/mobile/email) all share this one input.
	@FindBy(id = "username_input")
	WebElement idInputField;

	@FindBy(id = "error-banner-message")
	WebElement invalidIndividualIdErrorMessage;

	@FindBy(id = "sbi_vid")
	WebElement biometricVidField;

	@FindBy(id = "secure-biometric-interface-integration")
	WebElement biometricIntegrationContainer;

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo, "Verified is logo displayed");
	}

	public void clickOnLoginWithInji() {
		clickOnElement(loginWithInji, "Clicked on login with inji");
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

	public boolean isLoginWithKbiDisplayed() {
		return isElementDisplayed(loginWithKbiBtn);
	}

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
				By.xpath("//button[@role='option' and normalize-space()=" + toXpathLiteral(language) + "]"));
		clickOnElement(langOption, "Selected language option: " + language);
		By navLanguageButton = By.cssSelector("nav button[aria-haspopup='listbox']");
		new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout())).until(d -> {
			List<WebElement> buttons = d.findElements(navLanguageButton);
			return !buttons.isEmpty() && buttons.get(0).getText().trim().contains(language);
		});
	}

	/** Opens the dropdown and switches the UI to the given 3-letter language code's display name;
	 *  no-ops if unmapped. */
	public void selectLanguageByCode(String languageCode) {
		String displayName = utils.LanguageUtil.getDisplayName(languageCode);
		if (displayName == null || displayName.equals(languageCode)) {
			return;
		}
		clickOnLanguageDropdown();
		selectLanguage(displayName);
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

	public void clickOnMobileNumberOption() {
		clickOnElement(mobileNumberOption, "Selected mobile number as the login ID type");
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
		clickOnElement(prefixNumberField, "Clicked on Prefix Number select field");
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
		clickOnElement(vidOption, "Clicked on vid option button");
	}

	public boolean isInvalidIndividualIdErrorMessageIsDisplayed() {
		return isElementVisible(invalidIndividualIdErrorMessage,
				"Verified invalid individual id error message is displayed");
	}

	public void enterVid(String vid) {
		waitForElementVisible(idInputField);
		idInputField.clear();
		enterText(idInputField, vid, "Entered vid in vid field");
	}

	public void clickOnEmailOptionButton() {
		clickOnElement(emailOption, "Clicked on email option button");
	}

	public void enterEmail(String email) {
		waitForElementVisible(idInputField);
		idInputField.clear();
		enterText(idInputField, email, "Entered email in email field");
	}

	public boolean isBiometricIntegrationContainerDisplayed() {
		return isElementVisible(biometricIntegrationContainer,
				"Verified secure biometric interface integration container is displayed");
	}

	public boolean isBiometricVidOptionDisplayed() {
		return isElementVisible(vidOption, "Verified UIN/VID option is displayed on biometric screen");
	}

	public void clickOnBiometricVidOptionButton() {
		clickOnElement(vidOption, "Clicked on UIN/VID option on biometric screen");
	}

	public boolean isBiometricVidTextFieldDisplayed() {
		return isElementVisible(biometricVidField, "Verified VID text field is displayed on biometric screen");
	}

	private static final String SCANNING_DEVICES_MSG_KEY = "loadingMsgs.scanning_devices_msg";

	public boolean isScanningDevicesMessageDisplayed() {
		return waitForLocalizedTextWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY, getBiometricScanningWaitSeconds());
	}

	public boolean isRetryScanButtonNotDisplayedWhileScanning() {
		int waitSeconds = getBiometricScanningWaitSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> isLocalizedTextVisibleWithinBiometricContainer(SCANNING_DEVICES_MSG_KEY)
					&& !isRetryScanButtonVisible());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
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

	private static final By ICON_RETRY_BUTTON_SELECTOR = By.cssSelector(
			"#secure-biometric-interface-integration button[type='button'].sbd-cursor-pointer.sbd-ml-1, "
					+ "#secure-biometric-interface-integration div.sbd-dropdown_container + button[type='button'], "
					+ "#secure-biometric-interface-integration div.sbd-flex button[type='button'].sbd-cursor-pointer");

	public void clickOnBiometricDeviceScanRetryButton() {
		int waitSeconds = getBiometricDeviceDiscoveryTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		WebElement retryButton = wait.until(ExpectedConditions.elementToBeClickable(ICON_RETRY_BUTTON_SELECTOR));
		clickOnElement(retryButton, "Clicked on biometric device scan retry button");
	}

	private int getBiometricScanningWaitSeconds() {
		return parseTimeoutProperty("biometricScanningWaitSeconds", 15);
	}

	private int getBiometricDeviceDiscoveryTimeoutSeconds() {
		return parseTimeoutProperty("biometricDeviceDiscoveryTimeoutSeconds", 30);
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
		}

		String expectedMessage = ResourceBundleLoader.get("errors.no_devices_found_msg");
		if (expectedMessage.startsWith("!!MISSING_KEY:")) {
			LOGGER.warn("errors.no_devices_found_msg is missing from the resource bundle - "
					+ "device-not-found detection relied only on the English literal check above.");
			return false;
		}
		return containerText.contains(normalizeMessage(expectedMessage));
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
		List<WebElement> retryButtons = driver.findElements(
				By.xpath("//div[@id='secure-biometric-interface-integration']//button[contains("
						+ "translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),"
						+ "'retry')]"));
		if (retryButtons.stream().anyMatch(WebElement::isDisplayed)) {
			return true;
		}

		List<WebElement> iconRetryButtons = driver.findElements(ICON_RETRY_BUTTON_SELECTOR);
		return iconRetryButtons.stream().anyMatch(WebElement::isDisplayed);
	}

	private String normalizeMessage(String message) {
		return message == null ? "" : message.replaceAll("\\s+", " ").trim().toLowerCase();
	}

}