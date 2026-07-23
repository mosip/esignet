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
import org.openqa.selenium.support.ui.WebDriverWait;

import utils.EsignetConfigManager;
import utils.ResourceBundleLoader;

public class LoginOptionsPage extends BasePage {

	private static final By BIOMETRIC_INTEGRATION_CONTAINER = By.id("secure-biometric-interface-integration");
	private static final By BIOMETRIC_DEVICE_ALERT = By
			.cssSelector("#secure-biometric-interface-integration div[role='alert']");
	private static final By BIOMETRIC_DEVICE_RETRY_BUTTON = By.cssSelector(
			"#secure-biometric-interface-integration button[type='button'].sbd-cursor-pointer.sbd-ml-1, "
					+ "#secure-biometric-interface-integration div.sbd-dropdown_container + button[type='button'], "
					+ "#secure-biometric-interface-integration div.sbd-flex button[type='button'].sbd-cursor-pointer");
	private static final String DEVICE_NOT_FOUND_MESSAGE_KEY = "errors.no_devices_found_msg";
	private static final String DEVICE_NOT_FOUND_PARTIAL = "device not found";
	private static final String CONNECTIVITY_PARTIAL = "connectivity";

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
		try {
			waitForElementVisible(BIOMETRIC_INTEGRATION_CONTAINER);
			return true;
		} catch (Exception e) {
			return false;
		}
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

	public boolean isScanningDevicesMessageDisplayed() {
		return waitForTextWithinBiometricContainer(getScanningDevicesPartialText(), getBiometricScanningWaitSeconds());
	}

	public boolean isRetryScanButtonNotDisplayedWhileScanning() {
		int waitSeconds = getBiometricScanningWaitSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		try {
			wait.until(driver -> isTextVisibleWithinBiometricContainer(getScanningDevicesPartialText())
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

	public void clickOnBiometricDeviceScanRetryButton() {
		int waitSeconds = getBiometricDeviceDiscoveryTimeoutSeconds();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		WebElement retryButton = wait.until(ExpectedConditions.elementToBeClickable(BIOMETRIC_DEVICE_RETRY_BUTTON));
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

	private boolean waitForTextWithinBiometricContainer(String expectedMessage, int timeoutSeconds) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		try {
			wait.until(driver -> isTextVisibleWithinBiometricContainer(expectedMessage));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private boolean isDeviceNotFoundMessageVisible() {
		String containerText = getBiometricContainerText();
		if (containerText.contains(DEVICE_NOT_FOUND_PARTIAL) && containerText.contains(CONNECTIVITY_PARTIAL)) {
			return true;
		}

		try {
			List<WebElement> alerts = driver.findElements(BIOMETRIC_DEVICE_ALERT);
			for (WebElement alert : alerts) {
				if (alert.isDisplayed()) {
					String alertText = normalizeMessage(safeGetText(alert));
					if (alertText.contains(DEVICE_NOT_FOUND_PARTIAL) && alertText.contains(CONNECTIVITY_PARTIAL)) {
						return true;
					}
				}
			}
		} catch (StaleElementReferenceException ignored) {
			// DOM is still updating while SBI scans for devices; retry on next wait poll.
		}

		String expectedMessage = ResourceBundleLoader.get(DEVICE_NOT_FOUND_MESSAGE_KEY);
		return !expectedMessage.startsWith("!!MISSING_KEY:")
				&& containerText.contains(normalizeMessage(expectedMessage));
	}

	private String getScanningDevicesPartialText() {
		return "scanning devices";
	}

	private boolean isTextVisibleWithinBiometricContainer(String normalizedPartialText) {
		if (normalizedPartialText == null || normalizedPartialText.isBlank()) {
			return false;
		}
		return getBiometricContainerText().contains(normalizeMessage(normalizedPartialText));
	}

	private String getBiometricContainerText() {
		try {
			WebElement container = driver.findElement(BIOMETRIC_INTEGRATION_CONTAINER);
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
		List<WebElement> retryButtons = driver.findElements(
				By.xpath("//div[@id='secure-biometric-interface-integration']//button[contains("
						+ "translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),"
						+ "'retry')]"));
		if (retryButtons.stream().anyMatch(WebElement::isDisplayed)) {
			return true;
		}

		List<WebElement> iconRetryButtons = driver.findElements(BIOMETRIC_DEVICE_RETRY_BUTTON);
		return iconRetryButtons.stream().anyMatch(WebElement::isDisplayed);
	}

	private String normalizeMessage(String message) {
		return message == null ? "" : message.replaceAll("\\s+", " ").trim().toLowerCase();
	}

}