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

public class ConsentPage extends BasePage {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConsentPage.class);

	public ConsentPage(WebDriver driver) {
		super(driver);
	}

	@FindBy(id = "login_with_otp")
	WebElement loginWithOtpButton;

	@FindBy(id = "language_selection")
	WebElement languageSelection;

	@FindBy(id = "Otp_IND")
	WebElement mobileNumberField;

	@FindBy(id = "get_otp")
	WebElement getOtpButton;

	@FindBy(xpath = "//div[@class='pincode-input-container']/input")
	List<WebElement> otpInputFields;

	@FindBy(id = "verify_otp")
	WebElement verifyOtpButton;

	@FindBy(id = "navbar-header")
	WebElement proceedToAttentionScreen;

	@FindBy(id = "proceed-button")
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

	@FindBy(id = "language_selection")
	WebElement languageDropdown;

	@FindBy(id = "ar2")
	WebElement arabicLanguage;

	@FindBy(xpath = "//div[@class='h-screen']")
	WebElement rootContainer;

	@FindBy(xpath = "//label[@for='voluntary_claims']")
	WebElement voluntaryClaimsMasterToggle;

	@FindBy(xpath = "//input[@type='checkbox' and contains(@class, 'sr-only peer')]")
	List<WebElement> voluntaryClaimsSubToggles;

	@FindBy(xpath = "//input[@id='voluntary_claims']")
	WebElement voluntaryClaimsMasterCheckbox;

	@FindBy(xpath = "(//div[@class='divide-y'])[1]//li//div[contains(@class,'justify-start')]//label")
	List<WebElement> mandatoryClaimsElements;

	@FindBy(xpath = "(//div[@class='divide-y'])[2]/ul/li")
	List<WebElement> voluntaryClaimsElements;

	@FindBy(xpath = "//button[@id='essential_claims_tooltip']/following::div[@class='divide-y'][1]//label")
	private List<WebElement> essentialClaims;

	@FindBy(xpath = "//button[@id='voluntary_claims_tooltip']/following::div[@class='divide-y'][1]//label")
	private List<WebElement> voluntaryClaims;

	@FindBy(id = "continue")
	WebElement allowButtonInConsentScreen;

	@FindBy(xpath = "//p[@class='font-bold consent-timer-text']")
	WebElement consentTimer;

	@FindBy(xpath = "//div[@role='menuitem']")
	List<WebElement> languageDropdownItems;

	@FindBy(id = "continue")
	WebElement allowButton;

	@FindBy(xpath = "//div[@class=' css-1dimb5e-singleValue']")
	WebElement selectedLanguageDropdown;

	@FindBy(xpath = "//button[contains(@class,'flex items-center px-4')]")
	WebElement profileDropdown;

	@FindBy(xpath = "(//div[@class='font-semibold'])[1]")
	WebElement essentialClaimsHeader;

	@FindBy(xpath = "(//div[@class='divide-y'])[1]")
	WebElement essentialClaimsList;

	@FindBy(xpath = "//p[@class='text-[#4E4E4E] font-semibold']")
	WebElement actionMessage;

	@FindBy(id = "login-header")
	WebElement loginTitle;

	@FindBy(id = "login-subheader")
	WebElement loginSubTitle;

	@FindBy(xpath = "//h1[@class='text-base leading-5 font-sans font-medium my-2']")
	WebElement selectPreferredModeHeader;

	@FindBy(xpath = "//div[@class='inline mx-2 font-semibold my-3']")
	WebElement selectPreferredIdHeader;
	
	@FindBy(xpath = "//div[@class='header my-2']")
	WebElement headerInConsentUpdateProfileScreen;

	@FindBy(xpath = "//p[@class='sub-header m-0 mt-1 md:mx-5 md:mb-1 md:mt-3']")
	WebElement subHeaderInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//div[@class='font-semibold mb-1'])[1]")
	WebElement essentialClaimHeaderInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//div[@class='font-semibold mb-1'])[2]")
	WebElement voluntaryClaimHeaderInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//*[name()='svg' and contains(@class,'cursor-pointer')])[1]")
	WebElement essentialInfoIconInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//*[name()='svg' and contains(@class,'cursor-pointer')])[2]")
	WebElement voluntaryInfoIconInConsentUpdateProfileScreen;

	@FindBy(id = "cancel-button")
	WebElement cancelButtonInConsentUpdateProfileScreen;

	@FindBy(xpath = "(//div[@class='divide-y'])[2]")
	WebElement voluntaryClaimsList;

	@FindBy(xpath = "//span[@class='available-claim']")
	WebElement availableClaimsStatus;

	@FindBy(xpath = "//span[@class='not-available-claim']")
	WebElement notAvailableClaimStatus;

	@FindBy(xpath = "(//p[@class='mb-1'])[1]")
	WebElement infoIconMeassage;

	@FindBy(xpath = "//div[@class='message mx-0 px-2 mt-2 md:mx-5']")
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
		clickOnElement(loginWithOtpButton, "Clicked on login with Otp button");
	}

	public void enterRegisteredMobileNumber(String number) {
		waitForElementVisible(mobileNumberField);
		mobileNumberField.clear();
		enterText(mobileNumberField, number, "Entered registered mobile number");
	}

	public void clickOnGetOtp() {
		clickOnElement(getOtpButton, "Clicked on get otp button");
	}

	public String getCurrentLanguage() {
		waitForElementVisible(languageSelection);
		return languageSelection.getText().trim();
	}

	public void enterOtp(String otp) {
		waitForElementVisible(By.xpath("//div[@class='pincode-input-container']/input"));
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
		waitForElementVisible(proceedToAttentionScreen);
		return proceedToAttentionScreen.isDisplayed();
	}

	public void clickOnProceedButtonInAttentionPage() {
		clickOnElement(proceedButtonInAttentionPage, "Clicked on Procced button in attention screen");
	}

	public void clickOnProceedButton() {
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
			// Signup's "Verification Unsuccessful!" screen - this button renders only on failure.
			List<WebElement> verificationFailed = driverInstance.findElements(By.id("success-continue-button"));
			if (!verificationFailed.isEmpty() && verificationFailed.get(0).isDisplayed()) {
				throw new IllegalStateException("eKYC identity verification failed: signup reported "
						+ "'Verification Unsuccessful!' on the identity verification status screen");
			}

			// Signup's other failure paths redirect back to eSignet carrying an error query param.
			String currentUrl = driverInstance.getCurrentUrl();
			if (currentUrl != null && currentUrl.contains("error=")) {
				throw new IllegalStateException(
						"eKYC identity verification failed; redirected back with error: " + currentUrl);
			}

			List<WebElement> consentAllowButton = driverInstance.findElements(By.id("continue"));
			return !consentAllowButton.isEmpty() && consentAllowButton.get(0).isDisplayed();
		});
	}

	public boolean isConsentScreenVisible() {
		return isElementVisible(allowButton, "Verified is navigated to consent scrren");
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

	public void enableVoluntaryClaimsMasterToggle() {
		waitForElementVisible(voluntaryClaimsMasterToggle);
		if (!voluntaryClaimsMasterToggle.isSelected()) {
			clickOnElement(voluntaryClaimsMasterToggle, "Enabled the voluntary claims master toggle button");
		}
	}

	public void disableVoluntaryClaimsMasterToggle() {
		clickOnElement(voluntaryClaimsMasterToggle, "Disabled the voluntary claims master toggle button ");
	}

	public boolean isVoluntaryClaimsMasterToggleSelected() {
		waitForElementVisible(voluntaryClaimsMasterToggle);
		return voluntaryClaimsMasterCheckbox.isSelected();
	}

	public String getVoluntaryClaimsTooltipText() {
		return getTooltipText(By.id("voluntary_claims_tooltip"), By.xpath("//div[contains(@class,'react-tooltip')]"));
	}

	public void toggleVoluntaryClaim(String claimName, boolean enable) {
		String normalized = ClaimsUtil.normalizeClaim(claimName);
		By labelLocator = By.xpath("//label[@for='" + normalized + "']");
		By inputLocator = By.id(normalized);
		WebElement label = waitForElementVisible(labelLocator);
		WebElement checkbox = driver.findElement(inputLocator);
		if (checkbox.isSelected() != enable) {
			label.click();
		}
	}

	public boolean areEssentialClaimsPresent() {
		return !essentialClaims.isEmpty();
	}

	public boolean areVoluntaryClaimsPresent() {
		return !voluntaryClaims.isEmpty();
	}

	public void clickOnAllowBtnInConsentScreen() {
		clickOnElement(allowButtonInConsentScreen, "Clicked on allow button in consent screen");
	}

	public void enterVid(String vid) {
		WebElement vidField = waitForElementVisible(By.id("Otp_vid"));
		vidField.clear();
		enterText(vidField, vid, "Entered vid in vid field");
	}

	public boolean isAttentionScreenDisplayedNow() {
		List<WebElement> attentionHeaders = driver.findElements(By.id("navbar-header"));
		return !attentionHeaders.isEmpty() && attentionHeaders.get(0).isDisplayed();
	}

	public boolean isConsentScreenDisplayedNow() {
		List<WebElement> timers = driver.findElements(By.xpath("//p[@class='font-bold consent-timer-text']"));
		return !timers.isEmpty() && timers.get(0).isDisplayed();
	}

	public void waitForRelyingPartyRedirect() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("sign-in-with-esignet")));
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
			String url = driverInstance.getCurrentUrl();
			return url != null && !url.contains("/authorize") && !url.contains("esignet");
		});
	}

	public void completeConsentFlowThroughEkyc() {
		clickOnProceedButtonInAttentionPage();
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
		return driver.findElements(By.id("essential_claims_tooltip")).isEmpty()
				&& driver.findElements(By.id("voluntary_claims_tooltip")).isEmpty();
	}

	public void waitUntilUserProfilePage() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		wait.until(driverInstance -> isUserProfilePageDisplayed());
		LOGGER.info("Navigated to user profile page: {}", driver.getCurrentUrl());
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
		// Confirmed on both plugins: the timer always starts at 55 seconds and never crosses a
		// minute boundary, so this intentionally reads only the seconds portion - do not "fix" this
		// into a minutes*60+seconds calculation.
		String timerValue = consentTimer.getText().trim();
		String secondsPart = timerValue.split(":")[1];
		int seconds = Integer.parseInt(secondsPart);
		return seconds;
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