package pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;
import utils.ClaimsUtil;

public class ConsentPage extends BasePage {

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

	public void clickOnLoginWithOtp() {
		clickOnElement(loginWithOtpButton, "Clicked on login with Otp button");
	}

	public void enterRegisteredMobileNumber(String number) {
		mobileNumberField.clear();
		enterText(mobileNumberField, number, "Entered registered mobile number");
	}

	public void clickOnGetOtp() {
		clickOnElement(getOtpButton, "Clicked on get otp button");
	}

	public String getCurrentLanguage() {
		return languageSelection.getText().trim();
	}

	public void enterOtp(String otp) {
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
		return proceedToAttentionScreen.isDisplayed();
	}

	public void clickOnProceedButtonInAttentionPage() {
		clickOnElement(proceedButtonInAttentionPage, "Clicked on Procced button in attention screen");
	}

	public void clickOnProceedButton() {
		clickOnElement(proceedButton, "Clicked on proceed button");
	}

	public void clickOnMockIdentifyVerifier() {
		clickOnElement(eKycServiceProvider, "Selected the ekyc provider");
	}

	public void clickOnProceedButtonInServiceProviderPage() {
		clickOnElement(proceedButtonInServiceProviderPage, "clicked on proceed button in ekyc screen");
	}

	public void checkTermsAndCondition() {
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

	public void waitUntilLivenessCheckCompletes() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120));
		wait.until(ExpectedConditions.visibilityOf(allowButtonInConsentScreen));
	}

	private void clickWhenClickable(WebElement element) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		WebElement stableElement = wait
				.until(ExpectedConditions.refreshed(ExpectedConditions.elementToBeClickable(element)));
		stableElement.click();
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
		if (!voluntaryClaimsMasterToggle.isSelected()) {
			clickOnElement(voluntaryClaimsMasterToggle, "Enabled the voluntary claims master toggle button");
		}
	}

	public void disableVoluntaryClaimsMasterToggle() {
		clickOnElement(voluntaryClaimsMasterToggle, "Disabled the voluntary claims master toggle button ");
	}

	public boolean isVoluntaryClaimsMasterToggleSelected() {
		return voluntaryClaimsMasterCheckbox.isSelected();
	}

	public String getVoluntaryClaimsTooltipText() {
		return getTooltipText(By.id("voluntary_claims_tooltip"), By.xpath("//div[contains(@class,'react-tooltip')]"));
	}

	public void toggleVoluntaryClaim(String claimName, boolean enable) {
		String normalized = ClaimsUtil.normalizeClaim(claimName);
		By labelLocator = By.xpath("//label[@for='" + normalized + "']");
		By inputLocator = By.id(normalized);
		WebElement label = driver.findElement(labelLocator);
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
		String timerValue = consentTimer.getText().trim();
		String secondsPart = timerValue.split(":")[1];
		int seconds = Integer.parseInt(secondsPart);
		return seconds;
	}

	public String getSelectedLanguageFromDropdown() {
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
		return isElementVisible(essentialClaimsHeader);
	}

	public boolean isEssentialClaimsListDisplayed() {
		return isElementVisible(essentialClaimsList);
	}

	public boolean isActionMessageDisplayed() {
		return isElementVisible(actionMessage);
	}

	public boolean isTimerDisplayed() {
		return isElementVisible(consentTimer);
	}

}