package pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.aventstack.extentreports.Status;

import base.BasePage;
import utils.ClaimsUtil;
import utils.EsignetConfigManager;
import utils.ExtentReportManager;

public class ConsentPage extends BasePage {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConsentPage.class);

	public ConsentPage(WebDriver driver) {
		super(driver);
	}

	@FindBy(id = "acr_otp")
	WebElement loginWithOtpButton;

	@FindBy(css = "nav button[aria-haspopup='listbox']")
	WebElement languageSelection;

	@FindBy(id = "username_input")
	WebElement mobileNumberField;

	@FindBy(id = "login_id_mobile")
	WebElement mobileIdTypeButton;

	@FindBy(id = "submit_uin")
	WebElement getOtpButton;

	@FindBy(css = "input.thunderid-otp-field__input")
	List<WebElement> otpInputFields;

	@FindBy(id = "action_submit_otp")
	WebElement verifyOtpButton;

	@FindBy(id = "action_allow")
	WebElement proceedButtonInAttentionPage;

	@FindBy(xpath = "//button[contains(@class,'inline-flex items-center justify-center')][2]")
	WebElement proceedButton;

	@FindBy(id = "mock-identity-verifier")
	WebElement eKycServiceProvider;

	@FindBy(id = "proceed-preview-button")
	WebElement proceedButtonInServiceProviderPage;

	@FindBy(id = "consent-button")
	WebElement termsAndConditionCheckBox;

	@FindBy(id = "proceed-tnc-button")
	WebElement proceedBtnInTandCPage;

	@FindBy(id = "proceed-preview-button")
	WebElement proceedBtnInCameraPreviewPage;

	@FindBy(css = "nav button[aria-haspopup='listbox']")
	WebElement languageDropdown;

	@FindBy(xpath = "//button[@role='option' and normalize-space()='العربية']")
	WebElement arabicLanguage;

	@FindBy(tagName = "html")
	WebElement rootContainer;

	@FindBy(id = "consent_opt__all")
	WebElement voluntaryClaimsMasterToggle;

	@FindBy(css = "input.thunderid-toggle__input[id^='consent_opt__']:not(#consent_opt__all)")
	List<WebElement> voluntaryClaimsSubToggles;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[2]//div[contains(@class,'thunderid-consent-checkbox-list__item')]")
	List<WebElement> voluntaryClaimsElements;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[1]//div[contains(@class,'thunderid-consent-checkbox-list__item')]")
	private List<WebElement> essentialClaims;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[2]//div[contains(@class,'thunderid-consent-checkbox-list__item')]")
	private List<WebElement> voluntaryClaims;

	@FindBy(xpath = "//h5[@id='text_consent_title']/preceding-sibling::p[1]")
	WebElement consentTimer;

	@FindBy(xpath = "//div[@role='menuitem']")
	List<WebElement> languageDropdownItems;

	@FindBy(id = "action_allow")
	WebElement allowButton;

	@FindBy(xpath = "//div[@class=' css-1dimb5e-singleValue']")
	WebElement selectedLanguageDropdown;

	@FindBy(xpath = "//button[contains(@class,'flex items-center px-4')]")
	WebElement profileDropdown;

	@FindBy(xpath = "(//h6[contains(@class,'thunderid-typography__subtitle2')])[1]")
	WebElement essentialClaimsHeader;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[1]")
	WebElement essentialClaimsList;

	@FindBy(xpath = "//h5[@id='text_consent_title']/preceding-sibling::p[1]")
	WebElement actionMessage;

	@FindBy(id = "text_heading")
	WebElement loginTitle;

	@FindBy(css = "#text_heading + div")
	WebElement loginSubTitle;

	@FindBy(id = "acr_text_heading")
	WebElement selectPreferredModeHeader;

	@FindBy(xpath = "//div[@class='inline mx-2 font-semibold my-3']")
	WebElement selectPreferredIdHeader;

	@FindBy(id = "text_consent_title")
	WebElement headerInConsentUpdateProfileScreen;

	@FindBy(xpath = "//h5[@id='text_consent_title']/preceding-sibling::p[1]")
	WebElement subHeaderInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//h6[contains(@class,'thunderid-typography__subtitle2')])[1]")
	WebElement essentialClaimHeaderInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//h6[contains(@class,'thunderid-typography__subtitle2')])[2]")
	WebElement voluntaryClaimHeaderInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//div[@aria-label='More Info'])[1]")
	WebElement essentialInfoIconInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//div[@aria-label='More Info'])[2]")
	WebElement voluntaryInfoIconInConsentUpdateProfileScreen;

	@FindBy(id = "action_deny")
	WebElement cancelButtonInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[2]")
	WebElement voluntaryClaimsList;

	@FindBy(xpath = "//span[@class='available-claim']")
	WebElement availableClaimsStatus;

	@FindBy(xpath = "//span[@class='not-available-claim']")
	WebElement notAvailableClaimStatus;

	@FindBy(xpath = "//div[contains(@class,'thunderid-tooltip__container')]")
	WebElement infoIconMeassage;

	@FindBy(xpath = "//*[contains(text(),'Proceed') and contains(text(),'verification')]")
	WebElement messageAboveProceedBtn;

	@FindBy(xpath = "//div[@class='relative text-center text-dark font-semibold text-xl text-[#2B3840] mt-9']")
	WebElement attentionHeaderInWarningPopup;

	@FindBy(xpath = "//p[@class='text-base text-[#707070]']")
	WebElement subHeaderInWarningPopup;

	@FindBy(id = "stay-button")
	WebElement stayButtonInConsentUpdateProfileScreen;

	@FindBy(id = "discontinue-button")
	WebElement discontinueButtonInConsentUpdateProfileScreen;

	public boolean clickOnLoginWithOtp() {
		boolean landmarkReached = ensureFreshEsignetLoginPage(By.cssSelector("[id^='acr_'], #username_input"));
		if (!landmarkReached) {
			return false;
		}

		if (driver.findElements(By.id("acr_otp")).isEmpty() && !driver.findElements(By.id("username_input")).isEmpty()) {
			LOGGER.info("Login-with-Otp chooser not present but username_input already is - "
					+ "single-factor screen, nothing to click, proceeding directly.");
			return true;
		}
		clickOnElement(loginWithOtpButton, "Clicked on login with Otp button");
		waitForOtpIdEntryScreen();
		return true;
	}

	private void waitForOtpIdEntryScreen() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20))
					.pollingEvery(Duration.ofMillis(200))
					.ignoring(StaleElementReferenceException.class)
					.until(driverInstance -> !driverInstance.findElements(By.id("username_input")).isEmpty()
							|| !driverInstance.findElements(By.cssSelector("[id^='login_id_']")).isEmpty());
		} catch (TimeoutException e) {
			throw new TimeoutException(
					"OTP ID-entry screen did not appear after clicking Login with OTP. URL: "
							+ driver.getCurrentUrl(),
					e);
		}
	}

	public boolean isLoginWithOtpOptionVisible() {
		return isElementVisible(loginWithOtpButton, "Checked login with OTP option visibility");
	}

	public void enterRegisteredMobileNumber(String number) {
		new WebDriverWait(driver, Duration.ofSeconds(20))
				.pollingEvery(Duration.ofMillis(200))
				.ignoring(StaleElementReferenceException.class)
				.until(d -> !d.findElements(By.id("login_id_mobile")).isEmpty()
						|| !d.findElements(By.id("username_input")).isEmpty());
		for (WebElement chip : driver.findElements(By.id("login_id_mobile"))) {
			if (chip.isDisplayed()) {
				clickOnElement(chip, "Selected mobile number ID type");
				break;
			}
		}
		waitForElementVisible(mobileNumberField);
		mobileNumberField.clear();
		enterText(mobileNumberField, number, "Entered registered mobile number");
	}

	public void clickOnGetOtp() {
		syncLoginIdFieldBeforeGetOtp();
		assertLoginIdFieldPopulatedBeforeGetOtp();
		logLoginIdStateBeforeGetOtp();
		solveRecaptchaIfPresent();
		clickOnElement(getOtpButton, "Clicked on get otp button");
		waitForOtpSendOutcome();
	}

	private void logLoginIdStateBeforeGetOtp() {
		StringBuilder state = new StringBuilder("Get OTP with");
		for (WebElement field : driver.findElements(By.id("username_input"))) {
			if (field.isDisplayed()) {
				state.append(" individualId='").append(field.getAttribute("value")).append("'");
				break;
			}
		}
		for (String id : List.of("login_id_vid", "login_id_uin", "login_id_mobile", "login_id_email", "login_id_nrc")) {
			for (WebElement chip : driver.findElements(By.id(id))) {
				if (chip.isDisplayed()) {
					String cssClass = chip.getAttribute("class");
					if (cssClass != null && cssClass.contains("login-id-button--active")) {
						state.append(" activeType=").append(id);
					}
				}
			}
		}
		LOGGER.info(state.toString());
		try {
			ExtentReportManager.getTest().log(Status.INFO, state.toString());
		} catch (Exception ignored) {

		}
	}

	private void syncLoginIdFieldBeforeGetOtp() {
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
							+ "const desc = Object.getOwnPropertyDescriptor("
							+ "  window.HTMLInputElement.prototype, 'value');"
							+ "if (desc && desc.set) { desc.set.call(el, v); }"
							+ "else { el.value = v; }"
							+ "el.dispatchEvent(new Event('input', { bubbles: true }));"
							+ "el.dispatchEvent(new Event('change', { bubbles: true }));"
							+ "el.blur();",
					field, value);
			return;
		}
	}

	private void assertLoginIdFieldPopulatedBeforeGetOtp() {
		for (WebElement field : driver.findElements(By.id("username_input"))) {
			if (!field.isDisplayed()) {
				continue;
			}
			String value = field.getAttribute("value");
			if (value == null || value.isBlank()) {
				throw new IllegalStateException(
						"Login ID field is empty before Get OTP - select the correct ID type and re-enter the value");
			}
			return;
		}
	}

	public void waitForOtpVerificationScreen() {
		waitForOtpSendOutcome();
		if (!isOtpVerificationScreenDisplayed()) {
			throw new TimeoutException(
					"OTP verification screen did not appear after Get OTP. "
							+ readOtpSendFailureDetails()
							+ " URL: " + driver.getCurrentUrl());
		}
	}

	private void waitForOtpSendOutcome() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		wait.pollingEvery(Duration.ofMillis(500));
		wait.ignoring(StaleElementReferenceException.class);
		try {
			wait.until(driverInstance -> isOtpVerificationScreenDisplayed()
					|| readVisibleOtpSendError(driverInstance) != null);
		} catch (TimeoutException e) {
			throw new TimeoutException(
					"Get OTP produced neither an OTP screen nor an error. "
							+ readOtpSendFailureDetails()
							+ " URL: " + driver.getCurrentUrl(),
					e);
		}
		String sendOtpError = readVisibleOtpSendError(driver);
		if (sendOtpError != null && !isOtpVerificationScreenDisplayed()) {
			LOGGER.info("Get OTP outcome: {}", sendOtpError);
			try {
				ExtentReportManager.getTest().log(Status.INFO, "Get OTP outcome: " + sendOtpError);
			} catch (Exception ignored) {

			}
		}
	}

	private boolean isOtpVerificationScreenDisplayed() {
		for (WebElement otpField : driver.findElements(By.cssSelector("input.thunderid-otp-field__input"))) {
			if (otpField.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private String readOtpSendFailureDetails() {
		String banner = readVisibleOtpSendError(driver);
		if (banner != null) {
			return "error='" + banner + "'.";
		}
		for (WebElement field : driver.findElements(By.id("username_input"))) {
			if (!field.isDisplayed()) {
				continue;
			}
			String validation = field.getAttribute("validationMessage");
			String value = field.getAttribute("value");
			if (validation != null && !validation.isBlank()) {
				return "fieldValidation='" + validation + "' value='" + value + "'.";
			}
			return "individualId='" + value + "' and no error banner.";
		}
		return "username_input not visible.";
	}

	private String readVisibleOtpSendError(WebDriver driverInstance) {
		for (By locator : List.of(
				By.id("error-banner-message"),
				By.id("error-banner"),
				By.cssSelector("[role='alert']"),
				By.cssSelector("[class*='field__error']"),
				By.cssSelector("[class*='error-message']"))) {
			for (WebElement banner : driverInstance.findElements(locator)) {
				if (banner.isDisplayed()) {
					String text = banner.getText();
					if (text != null && !text.isBlank()) {
						return text.trim();
					}
				}
			}
		}
		for (WebElement field : driverInstance.findElements(By.id("username_input"))) {
			if (!field.isDisplayed()) {
				continue;
			}
			String validation = field.getAttribute("validationMessage");
			if (validation != null && !validation.isBlank()) {
				return validation.trim();
			}
		}
		return null;
	}

	public void enterOtp(String otp) {
		waitForElementVisible(By.cssSelector("input.thunderid-otp-field__input"));
		for (WebElement field : otpInputFields) {
			field.click();
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
			field.sendKeys(Keys.BACK_SPACE);
		}
		enterOtpDigits(otpInputFields, otp, (field, digit) -> {
			field.click();
			field.sendKeys(String.valueOf(digit));
		});
	}

	public String getCurrentLanguage() {
		waitForElementVisible(languageSelection);
		return languageSelection.getText().trim();
	}

	public void clickOnVerifyButton() {
		clickOnElement(verifyOtpButton, "Clicked on verify otp button");
	}

	public boolean isOnAttentionScreen() {
		return isElementVisible(proceedButtonInAttentionPage, "Verified attention screen proceed button is visible");
	}

	public static boolean refreshAfterAttentionProceed;

	public void clickOnProceedButtonInAttentionPage() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
		for (By locator : List.of(
				By.id("action_allow"),
				By.id("proceed-button"),
				By.xpath("//button[contains(normalize-space(.),'Allow') or contains(normalize-space(.),'Proceed')]"))) {
			try {
				WebElement proceed = wait.until(ExpectedConditions.elementToBeClickable(locator));
				clickOnElement(proceed, "Clicked on Proceed button in attention screen");
				if (refreshAfterAttentionProceed) {
					driver.navigate().refresh();
					new WebDriverWait(driver, Duration.ofSeconds(20))
							.until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState")
									.equals("complete"));
				}
				return;
			} catch (TimeoutException ignored) {

			}
		}
		throw new IllegalStateException("Proceed button not found on attention screen (action_allow / proceed-button)");
	}

	public void clickOnProceedButton() {
		if (waitForRelyingPartyRedirectOrElement(
				By.xpath("(//button[contains(@class,'inline-flex items-center justify-center')])[2]"), 5)) {
			LOGGER.info("Not clicking - login completed directly to the relying party with no eKYC sequence.");
			return;
		}

		if (!driver.findElements(By.xpath("//*[normalize-space(text())='Alert!']")).isEmpty()) {
			LOGGER.info("Not clicking - browser compatibility screen is already displayed.");
			return;
		}
		clickWhenClickable(proceedButton);
	}

	public void clickOnMockIdentifyVerifier() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		WebElement provider = wait.until(ExpectedConditions.elementToBeClickable(By.id("mock-identity-verifier")));
		clickOnElement(provider, "Selected the ekyc provider");
	}

	public void clickOnProceedButtonInServiceProviderPage() {
		clickWhenClickable(proceedButtonInServiceProviderPage);
	}

	public void checkTermsAndCondition() {
		waitForElementVisible(termsAndConditionCheckBox);
		if (!termsAndConditionCheckBox.isSelected()) {
			clickOnElement(termsAndConditionCheckBox, "Selected the terms and condition checkbox");
		}
	}

	public void clickOnProceedButtonInTermsAndConditionPage() {
		clickOnElement(proceedBtnInTandCPage, "Clicked on proceed button in terms and condition screen");
	}

	public void clickOnProceedButtonInCameraPreviewPage() {
		clickWhenClickable(proceedBtnInCameraPreviewPage);
	}

	public void completeEkycVerificationIfRequired() {
		if (driver.findElements(By.id("mock-identity-verifier")).isEmpty()) {
			LOGGER.info("Mock eKYC provider not shown; assuming repeat-auth flow without liveness");
			return;
		}
		clickOnMockIdentifyVerifier();
		clickOnProceedButtonInServiceProviderPage();
		checkTermsAndCondition();
		clickOnProceedButtonInTermsAndConditionPage();
		clickOnProceedButtonInCameraPreviewPage();
		waitUntilLivenessCheckCompletes();
	}

	public void waitUntilLivenessCheckCompletes() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(240));
		wait.pollingEvery(Duration.ofSeconds(2));
		wait.ignoring(NoSuchElementException.class, StaleElementReferenceException.class);

		wait.until(driverInstance -> {

			List<WebElement> verificationFailed = driverInstance.findElements(By.id("success-continue-button"));
			if (!verificationFailed.isEmpty() && verificationFailed.get(0).isDisplayed()) {
				throw new IllegalStateException("eKYC identity verification failed: signup reported "
						+ "'Verification Unsuccessful!' on the identity verification status screen");
			}

			String currentUrl = driverInstance.getCurrentUrl();
			if (currentUrl != null && currentUrl.contains("error=")) {
				throw new IllegalStateException(
						"eKYC identity verification failed; redirected back with error: " + currentUrl);
			}

			List<WebElement> consentAllowButton = driverInstance.findElements(By.id("action_allow"));
			return !consentAllowButton.isEmpty() && consentAllowButton.get(0).isDisplayed();
		});
	}

	public boolean isConsentScreenVisible() {
		return isElementVisible(allowButton, "Verified is navigated to consent scrren");
	}

	public boolean isOnAttentionScreen(int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(By.id("action_allow")));
			return true;
		} catch (org.openqa.selenium.TimeoutException e) {
			return false;
		}
	}

	public boolean isVoluntaryClaimsMasterToggleVisible() {
		return voluntaryClaimsElements.size() > 1
				&& isElementVisible(voluntaryClaimsMasterToggle, "Verified voluntary claims master toggle button");
	}

	public WebElement getVoluntaryClaimsMasterToggle() {
		return voluntaryClaimsMasterToggle;
	}

	public List<WebElement> getVoluntaryClaimsSubToggles() {
		return voluntaryClaimsSubToggles;
	}

	public List<String> getVoluntaryClaimNamesFromDom() {
		List<String> names = new ArrayList<>();
		for (WebElement toggle : voluntaryClaimsSubToggles) {
			String id = toggle.getAttribute("id");
			if (id != null && id.startsWith("consent_opt__")) {
				names.add(id.substring("consent_opt__".length()));
			}
		}
		return names;
	}

	public void enableVoluntaryClaimsMasterToggle() {
		waitForElementVisible(voluntaryClaimsMasterToggle);
		if (!voluntaryClaimsMasterToggle.isSelected()) {
			clickOnElement(voluntaryClaimsMasterToggle, "Enabled the voluntary claims master toggle button");
		}
	}

	public void disableVoluntaryClaimsMasterToggle() {
		waitForElementVisible(voluntaryClaimsMasterToggle);
		if (voluntaryClaimsMasterToggle.isSelected()) {
			clickOnElement(voluntaryClaimsMasterToggle, "Disabled the voluntary claims master toggle button");
		}
	}

	public boolean isVoluntaryClaimsMasterToggleSelected() {
		waitForElementVisible(voluntaryClaimsMasterToggle);
		return voluntaryClaimsMasterToggle.isSelected();
	}

	public String getVoluntaryClaimsTooltipText() {

		return getTooltipText(By.xpath("(//div[@aria-label='More Info'])[2]"), By.cssSelector("[role='tooltip']"));
	}

	public void toggleVoluntaryClaim(String claimName, boolean enable) {
		WebElement checkbox = findVoluntaryClaimToggle(claimName);
		if (checkbox.isSelected() != enable) {

			clickOnElement(checkbox, "Toggled voluntary claim '" + claimName + "' to " + enable);
		}
	}

	private WebElement findVoluntaryClaimToggle(String claimName) {
		By exactId = By.id("consent_opt__" + claimName);
		if (!driver.findElements(exactId).isEmpty()) {
			return waitForElementVisible(exactId);
		}

		String wanted = ClaimsUtil.normalizeClaim(claimName);
		for (WebElement toggle : getVoluntaryClaimsSubToggles()) {
			String id = toggle.getAttribute("id");
			if (id == null || !id.startsWith("consent_opt__")) {
				continue;
			}
			String suffix = id.substring("consent_opt__".length());
			if (wanted.equals(ClaimsUtil.normalizeClaim(suffix))) {
				return waitForElementVisible(By.id(id));
			}
		}

		return waitForElementVisible(exactId);
	}

	public boolean areEssentialClaimsPresent() {

		try {
			new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> !essentialClaims.isEmpty());
		} catch (org.openqa.selenium.TimeoutException ignored) {
		}
		return !essentialClaims.isEmpty();
	}

	public boolean areVoluntaryClaimsPresent() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> !voluntaryClaims.isEmpty());
		} catch (org.openqa.selenium.TimeoutException ignored) {
		}
		return !voluntaryClaims.isEmpty();
	}

	public void clickOnAllowBtnInConsentScreen() {
		clickOnElement(allowButton, "Clicked on allow button in consent screen");
	}

	public void enterVid(String vid) {
		WebElement vidField = waitForElementVisible(By.id("username_input"));
		vidField.clear();
		enterText(vidField, vid, "Entered vid in vid field");
	}

	public boolean isAttentionScreenDisplayedNow() {
		return isConsentScreenDisplayedNow();
	}

	public boolean isConsentScreenDisplayedNow() {
		List<WebElement> consentBlocks = driver.findElements(By.id("block_consent"));
		return !consentBlocks.isEmpty() && consentBlocks.get(0).isDisplayed();
	}

	public void waitForRelyingPartyRedirect() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		wait.until(driverInstance -> isAlreadyOnRelyingParty());
	}

	public void assertAuthenticationCompletedWithoutConsent() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		wait.pollingEvery(Duration.ofMillis(500));
		wait.until(driverInstance -> {
			if (isAttentionScreenDisplayedNow()) {
				throw new AssertionError("Attention screen was displayed when consent should be skipped");
			}
			if (isConsentScreenDisplayedNow()) {
				throw new AssertionError("Consent screen was displayed when consent should be skipped");
			}
			return isAlreadyOnRelyingParty();
		});
	}

	public void completeConsentFlowThroughEkyc() {

		clickOnProceedButtonInAttentionPage();
		if (waitForRelyingPartyRedirectQuietly()) {
			return;
		}
		clickOnProceedButton();
		if (driver.findElements(By.id("mock-identity-verifier")).isEmpty()) {
			LOGGER.info("Mock eKYC provider not shown; assuming repeat-auth flow without liveness");
			return;
		}
		clickOnMockIdentifyVerifier();
		clickOnProceedButtonInServiceProviderPage();
		checkTermsAndCondition();
		clickOnProceedButtonInTermsAndConditionPage();
		clickOnProceedButtonInCameraPreviewPage();
		waitUntilLivenessCheckCompletes();
		if (isConsentScreenDisplayedNow()) {
			throw new AssertionError("Consent screen was displayed when consent should be skipped");
		}
	}

	public void completeConsentRegistryFlowDecliningOptionalClaims() throws Exception {
		clickOnProceedButtonInAttentionPage();
		clickOnProceedButton();

		if (isAlreadyOnRelyingParty()) {
			LOGGER.info("Not declining claims - login completed directly to the relying party (consent "
					+ "already granted from an earlier scenario reusing this identity/client).");
			return;
		}
		completeEkycVerificationIfRequired();
		for (String claim : getClaims("voluntary")) {
			toggleVoluntaryClaim(claim, false);
		}
		clickOnAllowBtnInConsentScreen();
		waitUntilUserProfilePage();
	}

	public void completeConsentFlowThroughEkycIfAttentionScreenIsDisplayed() {
		if (isAttentionScreenDisplayedNow()) {
			completeConsentFlowThroughEkyc();
		}
	}

	public void waitUntilConsentScreenAfterAuthentication() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		wait.pollingEvery(Duration.ofMillis(500));
		wait.ignoring(NoSuchElementException.class, StaleElementReferenceException.class);
		wait.until(driverInstance -> {
			String currentUrl = driverInstance.getCurrentUrl();
			if (currentUrl != null && currentUrl.contains("error=")) {
				throw new IllegalStateException("Authentication failed; redirected back with error: " + currentUrl);
			}

			return isConsentUiDisplayedNow();
		});
	}

	private boolean isConsentUiDisplayedNow() {
		return isDisplayedNow(By.id("block_consent")) || isDisplayedNow(By.id("text_consent_title"))
				|| isDisplayedNow(By.id("action_allow")) || isAuthorizeScopePresentNow();
	}

	private boolean isDisplayedNow(By locator) {
		List<WebElement> found = driver.findElements(locator);
		return !found.isEmpty() && found.get(0).isDisplayed();
	}

	public boolean isAuthorizeScopeSectionDisplayed() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(3)).pollingEvery(Duration.ofMillis(300))
					.until(driverInstance -> isAuthorizeScopeSectionPresentNow());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private boolean isAuthorizeScopeSectionPresentNow() {
		if (isDisplayedNow(By.id("authorize_scope_tooltip"))) {
			return true;
		}
		return isDisplayedNow(By.xpath(
				"//*[contains(@class,'thunderid-typography') and (normalize-space()='Authorize Scopes'"
						+ " or contains(normalize-space(.),'Authorize Scopes'))]"))
				|| isAuthorizeScopePresentNow();
	}

	public boolean isAuthorizeScopeDisplayed(String scopeName) {
		if (isAuthorizeScopePresent(scopeName)) {
			return true;
		}
		try {
			new WebDriverWait(driver, Duration.ofSeconds(5)).pollingEvery(Duration.ofMillis(300))
					.until(driverInstance -> isAuthorizeScopePresent(scopeName));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private boolean isAuthorizeScopePresentNow() {
		return isAuthorizeScopePresent("Manage-VID");
	}

	private boolean isAuthorizeScopePresent(String scopeName) {
		WebElement toggle = findAuthorizeScopeToggle(scopeName);
		if (toggle != null && toggle.isDisplayed()) {
			return true;
		}
		String literal = toXpathLiteral(scopeName);
		return isDisplayedNow(By.xpath("//*[contains(@class,'thunderid-consent-checkbox-list') and contains(normalize-space(.),"
				+ literal + ")]"))
				|| isDisplayedNow(By.xpath("//*[contains(@id," + literal + ")]"))
				|| isDisplayedNow(By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));
	}

	public void toggleAuthorizeScope(String scopeName, boolean enable) {
		WebElement toggle = findAuthorizeScopeToggle(scopeName);
		if (toggle == null) {

			LOGGER.info("Authorize scope {} has no toggle; treating it as required", scopeName);
			return;
		}
		if (toggle.isSelected() == enable) {
			return;
		}
		String toggleId = toggle.getAttribute("id");
		if (toggleId != null && !toggleId.isBlank()) {
			List<WebElement> labels = driver.findElements(By.cssSelector("label[for='" + toggleId + "']"));
			if (!labels.isEmpty()) {
				clickOnElement(labels.get(0), "Toggled authorize scope " + scopeName + " to " + enable);
				return;
			}
		}
		clickOnElement(toggle, "Toggled authorize scope " + scopeName + " to " + enable);
	}

	private WebElement findAuthorizeScopeToggle(String scopeName) {
		List<By> candidates = List.of(
				By.id(scopeName),
				By.cssSelector("input.thunderid-toggle__input[id*='" + scopeName + "']"),
				By.xpath("//input[contains(@id," + toXpathLiteral(scopeName) + ")]"));
		for (By locator : candidates) {
			List<WebElement> found = driver.findElements(locator);
			if (!found.isEmpty()) {
				return found.get(0);
			}
		}
		return null;
	}

	public boolean areClaimSectionsAbsent() {

		for (String claimLabel : List.of("Full Name", "Email Address", "Phone Number", "Birthdate", "Gender",
				"Picture", "Address")) {
			List<WebElement> claimRows = driver.findElements(By.xpath(
					"//*[contains(@class,'thunderid-consent-checkbox-list__item') and contains(normalize-space(.),"
							+ toXpathLiteral(claimLabel) + ")]"));
			if (!claimRows.isEmpty() && claimRows.get(0).isDisplayed()) {
				return false;
			}
		}
		List<WebElement> claimToggles = driver.findElements(
				By.cssSelector("input[id^='consent_opt__']:not(#consent_opt__all), input[id*='_email'], input[id*='_gender']"));
		for (WebElement toggle : claimToggles) {
			String id = toggle.getAttribute("id");
			if (id != null && !id.contains("Manage-VID") && toggle.isDisplayed()) {
				return false;
			}
		}
		return true;
	}

	public void waitUntilUserProfilePage() {
		String relyingPartyBase = EsignetConfigManager.getproperty("baseurl");
		String normalizedRpBase = relyingPartyBase != null ? relyingPartyBase.replaceAll("/+$", "") : "";
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(90));
		wait.pollingEvery(Duration.ofMillis(500));
		wait.until(driverInstance -> {
			String currentUrl = driverInstance.getCurrentUrl();
			if (currentUrl != null && currentUrl.contains("error=session_expired")) {
				throw new IllegalStateException(
						"OAuth session expired before redirect to user profile; relaunch authorize URL and retry. URL: "
								+ currentUrl);
			}
			if (isUserProfilePageDisplayed()) {
				return true;
			}
			return currentUrl != null && !normalizedRpBase.isEmpty() && currentUrl.startsWith(normalizedRpBase)
					&& currentUrl.contains("code=");
		});
		String currentUrl = driver.getCurrentUrl();
		String sanitizedUrl = currentUrl != null && currentUrl.contains("?")
				? currentUrl.substring(0, currentUrl.indexOf('?'))
				: currentUrl;
		LOGGER.info("Navigated to user profile page: {}", sanitizedUrl);
	}

	public boolean isUserProfilePageDisplayed() {
		String currentUrl = driver.getCurrentUrl();
		return currentUrl != null && currentUrl.contains("userprofile") && currentUrl.contains("code=");
	}

	public boolean isLanguageDropdownDisplayed() {
		return isElementVisible(languageDropdown, "Verified language dropdown is visible");
	}

	public void clickOnLanguageDropdown() {
		clickOnElement(languageDropdown, "Clicked on language dropdown");
	}

	public void clickOnArabicLanguage() {
		clickOnElement(arabicLanguage, "Selected arabic language from dropdown");
	}

	public String getPageDirection() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
		wait.until(ExpectedConditions.attributeToBe(rootContainer, "dir", "rtl"));
		return rootContainer.getAttribute("dir");
	}

	public int getConsentTimerSeconds() {
		waitForElementVisible(consentTimer);
		String timerValue = consentTimer.getText().trim();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+):(\\d{2})").matcher(timerValue);
		if (!matcher.find()) {
			throw new IllegalStateException(
					"Could not parse consent timer text '" + timerValue + "' - expected a 'mm:ss'-style value");
		}
		return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
	}

	public String getSelectedLanguageFromDropdown() {
		waitForElementVisible(selectedLanguageDropdown);
		return selectedLanguageDropdown.getText().trim();
	}

	public void clickOnProfileDropdown() {
		clickOnElement(profileDropdown, "Clicked on profile dropdown");
	}

	public List<String> getDisplayedClaims() {
		List<String> claims = new ArrayList<>();
		List<WebElement> claimElements = driver.findElements(By.xpath("//a[contains(@class,'px-4 py-2 text-sm')]"));
		for (WebElement element : claimElements) {
			claims.add(element.getText().trim());
		}
		List<WebElement> profileElements = driver.findElements(By.xpath("//img[@class='h-12 w-12 ml-3 mr-3']"));
		if (!profileElements.isEmpty() && profileElements.get(0).isDisplayed()) {
			claims.add("Profile");
		}

		return claims;
	}

	public boolean isEssentialClaimsHeaderDisplayed() {
		return isElementVisible(essentialClaimsHeader, "Verified essential claims header is visible");
	}

	public boolean isEssentialClaimsListDisplayed() {
		return isElementVisible(essentialClaimsList, "Verified essential claims list is visible");
	}

	public boolean isActionMessageDisplayed() {
		return isElementVisible(actionMessage, "Verified action message is displayed");
	}

	public boolean isTimerDisplayed() {
		return isElementVisible(consentTimer, "Verified timer is displayed");
	}

	public boolean isVerifyOtpButtonEnabled() {
		return isButtonEnabled(verifyOtpButton, "Verified otp verification button is enabled");
	}

	public boolean isLoginWithOtpDisplayed(String expectedText) {
		try {
			waitForElementVisible(loginWithOtpButton);
		} catch (Exception e) {
			LOGGER.warn("Login with OTP button not visible or timed out", e);
			return false;
		}
		return loginWithOtpButton.getText().trim().startsWith(expectedText);
	}

	public boolean isLoginTitleDisplayed() {
		waitForElementVisible(loginWithOtpButton);
		return isElementDisplayed(loginTitle);
	}

	public boolean isLoginSubTitleDisplayed() {
		waitForElementVisible(loginWithOtpButton);
		return isElementDisplayed(loginSubTitle);
	}

	public String getLoginTitleText() {
		waitForElementVisible(loginTitle);
		return loginTitle.getText().trim();
	}

	public String getLoginSubTitleText() {
		waitForElementVisible(loginSubTitle);
		return loginSubTitle.getText().trim();
	}

	public boolean assertDefaultLoginTitleAndSubtitleIfEnglish() {
		String currentLang = System.getProperty("currentRunLanguage", "eng");
		if (!"eng".equalsIgnoreCase(currentLang)) {
			return false;
		}
		String title = getLoginTitleText();
		String subtitle = getLoginSubTitleText();
		Assert.assertEquals(title, "Login", "Title text mismatch after opening the authorize URL");
		Assert.assertTrue(subtitle.contains("is requesting authentication for login"),
				"Subtitle text mismatch after opening the authorize URL. Actual: " + subtitle);
		ExtentReportManager.logStep("Verified login title '" + title + "' and subtitle '" + subtitle + "'");
		return true;
	}

	public String getSelectPreferredModeHeaderText() {
		waitForElementVisible(selectPreferredModeHeader);
		return selectPreferredModeHeader.getText().trim();
	}

	public String getSelectPreferredIdHeaderText() {
		waitForElementVisible(selectPreferredIdHeader);
		return selectPreferredIdHeader.getText().trim();
	}

	public boolean isHeaderInConsentUpdateProfileScreenVisible() {
		return isElementVisible(headerInConsentUpdateProfileScreen, "Verified header in consent update profile screen");
	}

	public boolean isSubHeaderInConsentUpdateProfileScreenVisible() {
		return isElementVisible(subHeaderInConsentUpdateProfileScreen,
				"Verified sub header in consent update profile screen");
	}

	public boolean isEssentialClaimsHeaderInConsentUpdateProfileScreenVisible() {
		return isElementVisible(essentialClaimHeaderInConsentUpdateProfileScreen,
				"Verified essential claims header in consent update profile screen");
	}

	public boolean isVoluntaryClaimsHeaderInConsentUpdateProfileScreenVisible() {
		return isElementVisible(voluntaryClaimHeaderInConsentUpdateProfileScreen,
				"Verified voluntary claims header in consent update profile screen");
	}

	public boolean isInfoIconInConsentUpdateProfileScreenVisible() {
		return isElementVisible(essentialInfoIconInConsentUpdateProfileScreen,
				"Verified info icon in consent update profile screen");
	}

	public boolean isProceedButtonInConsentUpdateProfileScreenVisible() {
		return isElementVisible(proceedButtonInAttentionPage,
				"Verified procced button in consent update profile screen");
	}

	public boolean isCancelButtonInConsentUpdateProfileScreenVisible() {
		return isElementVisible(cancelButtonInConsentUpdateProfileScreen,
				"Verified cancel button in consent update profile screen");
	}

	public boolean isEssentialClaimListInConsentUpdateProfileScreenVisible() {
		return isElementVisible(essentialClaimsList, "Verified essential claim list in consent update profile screen");
	}

	public boolean isVoluntaryClaimListInConsentUpdateProfileScreenVisible() {
		return isElementVisible(voluntaryClaimsList, "Verified voluntary claim list in consent update profile screen");
	}

	public boolean isAvailableClaimStausDisplayed() {
		return isElementVisible(availableClaimsStatus,
				"Verified available claim status in consent update profile screen");
	}

	public boolean isNotAvailableClaimStausDisplayed() {
		return isElementVisible(notAvailableClaimStatus,
				"Verified not available claim status in consent update profile screen");
	}

	public void clickOnEssentialInfoIcon() {
		clickOnElement(essentialInfoIconInConsentUpdateProfileScreen,
				"Clicked on Info icon in consent update profile screen");
	}

	public void clickOnVoluntaryInfoIcon() {
		clickOnElement(voluntaryInfoIconInConsentUpdateProfileScreen,
				"Clicked on Info icon in consent update profile screen");
	}

	public void clickOnAttentionHeader() {
		clickOnElement(headerInConsentUpdateProfileScreen, "Clicked on header in consent update profile screen");
	}

	public boolean isEssentialClaimInformationDisplayed() {
		return isElementVisible(infoIconMeassage, "Verified essential claim information is displayed");
	}

	public boolean isVoluntaryClaimInformationDisplayed() {
		return isElementVisible(infoIconMeassage, "Verified voluntary claim information is displayed");
	}

	public boolean isMessageAboveProceedButtonDisplayed() {
		return isElementVisible(messageAboveProceedBtn,
				"Verified message above the proceed button in consent update profile screen");
	}

	public void clickOnCancelButtonInUpdateProfilePage() {
		clickOnElement(cancelButtonInConsentUpdateProfileScreen,
				"Clicked on cancel button in consent update profile screen");
	}

	public boolean isAttentionWarningPopupDisplayed() {
		return isElementVisible(attentionHeaderInWarningPopup, "Verified header in warning popup");
	}

	public boolean isSubHeaderInWarningPopupDisplayed() {
		return isElementVisible(subHeaderInWarningPopup, "Verified sub header in warning popup");
	}

	public boolean isStayButtonInWarningPopupScreenDisplayed() {
		return isElementVisible(stayButtonInConsentUpdateProfileScreen,
				"Verified stay button in warning popup is displayed");
	}

	public void clickOnStayButton() {
		clickOnElement(stayButtonInConsentUpdateProfileScreen, "Clicked on stay button");
	}

	public boolean isDiscontinueButtonInWarningPopupScreenDisplayed() {
		return isElementVisible(discontinueButtonInConsentUpdateProfileScreen,
				"Verified discontinue button in warning popup is displayed");
	}

	public void clickOnDiscontinueButton() {
		clickOnElement(discontinueButtonInConsentUpdateProfileScreen, "Clicked on discontinue button");
	}
}
