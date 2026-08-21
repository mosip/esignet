package pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import base.BasePage;
import utils.ClaimsUtil;
import utils.EsignetUtil;

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

	@FindBy(id = "consent_opt__all")
	WebElement voluntaryClaimsMasterCheckbox;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[1]//div[contains(@class,'thunderid-consent-checkbox-list__item')]")
	List<WebElement> mandatoryClaimsElements;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[2]//div[contains(@class,'thunderid-consent-checkbox-list__item')]")
	List<WebElement> voluntaryClaimsElements;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[1]//div[contains(@class,'thunderid-consent-checkbox-list__item')]")
	private List<WebElement> essentialClaims;

	@FindBy(xpath = "(//div[contains(concat(' ',normalize-space(@class),' '),' thunderid-consent-checkbox-list ')])[2]//div[contains(@class,'thunderid-consent-checkbox-list__item')]")
	private List<WebElement> voluntaryClaims;

	@FindBy(id = "action_allow")
	WebElement allowButtonInConsentScreen;

	@FindBy(xpath = "//p[contains(text(),'appropriate action')]")
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

	@FindBy(xpath = "//p[contains(text(),'appropriate action')]")
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

	public void clickOnLoginWithOtp() {
		ensureFreshEsignetLoginPage(By.cssSelector("[id^='acr_'], #username_input"));
		if (driver.findElements(By.id("acr_otp")).isEmpty() && !driver.findElements(By.id("username_input")).isEmpty()) {
			LOGGER.info("Login-with-Otp chooser not present but username_input already is - "
					+ "single-factor screen, nothing to click, proceeding directly.");
			return;
		}
		clickOnElement(loginWithOtpButton, "Clicked on login with Otp button");
	}

	public void enterRegisteredMobileNumber(String number) {
		clickOnElement(mobileIdTypeButton, "Selected mobile number ID type");
		waitForElementVisible(mobileNumberField);
		mobileNumberField.clear();
		enterText(mobileNumberField, number, "Entered registered mobile number");
	}

	public void clickOnGetOtp() {
		solveRecaptchaIfPresent();
		clickOnElement(getOtpButton, "Clicked on get otp button");
	}

	public String getCurrentLanguage() {
		waitForElementVisible(languageSelection);
		return languageSelection.getText().trim();
	}

	public void enterOtp(String otp) {
		waitForElementVisible(By.cssSelector("input.thunderid-otp-field__input"));
		if (otp.length() > otpInputFields.size()) {
			throw new IllegalArgumentException(
					"OTP length " + otp.length() + " exceeds rendered inputs " + otpInputFields.size());
		}
		for (WebElement field : otpInputFields) {
			field.click();
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
			field.sendKeys(Keys.BACK_SPACE);
		}
		for (int i = 0; i < otp.length(); i++) {
			WebElement field = otpInputFields.get(i);
			field.click();
			field.sendKeys(String.valueOf(otp.charAt(i)));
		}
	}

	public void clickOnVerifyButton() {
		clickOnElement(verifyOtpButton, "Clicked on verify otp button");
	}

	public boolean isOnAttentionScreen() {
		return isElementVisible(proceedButtonInAttentionPage, "Verified attention screen proceed button is visible");
	}

	public void clickOnProceedButtonInAttentionPage() {
		clickOnElement(proceedButtonInAttentionPage, "Clicked on Procced button in attention screen");
	}

	public void clickOnProceedButton() {
		if ("mock".equalsIgnoreCase(EsignetUtil.getPluginName())) {
			LOGGER.info("Not clicking (this step only, not the scenario) - no separate eKYC sequence "
					+ "exists after consent under this environment's mock-plugin flow - verified live.");
			return;
		}
		if (waitForRelyingPartyRedirectOrElement(
				By.xpath("(//button[contains(@class,'inline-flex items-center justify-center')])[2]"), 60)) {
			LOGGER.info("Not clicking - login completed directly to the relying party with no eKYC sequence.");
			return;
		}
		clickWhenClickable(proceedButton);
	}

	public void clickOnMockIdentifyVerifier() {
		clickOnElement(eKycServiceProvider, "Selected the ekyc provider");
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

	/**
	 * Waits for eKYC identity verification to finish. Signup polls its status endpoint for up to
	 * 200s by design (mosip.signup.status.request.limit=10 x status.request.delay=20s), so the
	 * timeout must outlast that budget rather than cutting the app off mid-poll. Rather than always
	 * burning the full timeout, this resolves as soon as either outcome is reached: the eSignet
	 * consent screen (success), or one of signup's failure paths (fails fast with the reason).
	 */
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
		if (voluntaryClaimsMasterCheckbox.isSelected()) {
			clickOnElement(voluntaryClaimsMasterToggle, "Disabled the voluntary claims master toggle button");
		}
	}

	public boolean isVoluntaryClaimsMasterToggleSelected() {
		waitForElementVisible(voluntaryClaimsMasterToggle);
		return voluntaryClaimsMasterCheckbox.isSelected();
	}

	public String getVoluntaryClaimsTooltipText() {
		return getTooltipText(By.xpath("(//div[@aria-label='More Info'])[2]"), By.cssSelector("[role='tooltip']"));
	}

	public void toggleVoluntaryClaim(String claimName, boolean enable) {
		String normalized = ClaimsUtil.normalizeClaim(claimName);
		WebElement checkbox = waitForElementVisible(By.id("consent_opt__" + normalized));
		if (checkbox.isSelected() != enable) {
			clickOnElement(checkbox, "Toggled voluntary claim '" + claimName + "' to " + enable);
		}
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
		clickOnElement(allowButtonInConsentScreen, "Clicked on allow button in consent screen");
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
		clickOnMockIdentifyVerifier();
		clickOnProceedButtonInServiceProviderPage();
		checkTermsAndCondition();
		clickOnProceedButtonInTermsAndConditionPage();
		clickOnProceedButtonInCameraPreviewPage();
		waitUntilLivenessCheckCompletes();
		clickOnAllowBtnInConsentScreen();
		waitForRelyingPartyRedirect();
	}

	public void completeConsentFlowThroughEkycIfAttentionScreenIsDisplayed() {
		if (isAttentionScreenDisplayedNow()) {
			completeConsentFlowThroughEkyc();
		}
	}

	public void waitUntilConsentScreenAfterAuthentication() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		wait.pollingEvery(Duration.ofSeconds(1));
		wait.ignoring(NoSuchElementException.class, StaleElementReferenceException.class);
		wait.until(driverInstance -> {
			String currentUrl = driverInstance.getCurrentUrl();
			if (currentUrl != null && currentUrl.contains("error=")) {
				throw new IllegalStateException("Authentication failed; redirected back with error: " + currentUrl);
			}
			return currentUrl != null && (currentUrl.contains("/consent") || isConsentScreenVisible());
		});
	}

	public boolean isAuthorizeScopeSectionDisplayed() {
		return !driver.findElements(By.id("authorize_scope_tooltip")).isEmpty();
	}

	public boolean isAuthorizeScopeDisplayed(String scopeName) {
		List<WebElement> scopeToggles = driver.findElements(By.id(scopeName));
		return !scopeToggles.isEmpty() && scopeToggles.get(0).isDisplayed();
	}

	public void toggleAuthorizeScope(String scopeName, boolean enable) {
		WebElement toggle = driver.findElement(By.id(scopeName));
		if (toggle.isSelected() != enable) {
			WebElement label = driver.findElement(By.cssSelector("label[for='" + scopeName + "']"));
			clickOnElement(label, "Toggled authorize scope " + scopeName + " to " + enable);
		}
	}

	public boolean areClaimSectionsAbsent() {
		return essentialClaims.isEmpty() && voluntaryClaims.isEmpty();
	}

	public void waitUntilUserProfilePage() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		wait.until(driverInstance -> isUserProfilePageDisplayed());
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
		String[] parts = timerValue.split(":");
		if (parts.length < 2) {
			throw new IllegalStateException(
					"Could not parse consent timer text '" + timerValue + "' - expected a 'mm:ss'-style value");
		}
		int minutes = Integer.parseInt(parts[0].replaceAll("\\D", ""));
		int seconds = Integer.parseInt(parts[1].replaceAll("\\D", ""));
		return minutes * 60 + seconds;
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

	/**
	 * The purpose-type scenarios assert on this button as their first step, so the /authorize page
	 * may still be resolving oauth-details (showing its loading spinner) when this runs. Wait for
	 * the button to render before reading its text, otherwise the check races the page load and
	 * fails intermittently in a full suite run while passing in isolation.
	 */
	public boolean isLoginWithOtpDisplayed(String expectedText) {
		try {
			waitForElementVisible(loginWithOtpButton);
		} catch (Exception e) {
			LOGGER.warn("Login with OTP button not visible or timed out", e);
			return false;
		}
		return loginWithOtpButton.getText().trim().startsWith(expectedText);
	}

	/**
	 * These back the "no title/subtitle should be displayed" assertions. They wait for the login
	 * page itself to render first - otherwise, on a page that is still loading, the title is
	 * trivially absent and the assertion would pass for the wrong reason.
	 */
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