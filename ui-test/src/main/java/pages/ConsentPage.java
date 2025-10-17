package pages;

import base.BasePage;
import utils.ResourceBundleLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ConsentPage extends BasePage {

	public ConsentPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "sign-in-with-esignet")
	WebElement signInWithEsignet;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(id = "login_with_walletname")
	WebElement loginWithInji;

	@FindBy(id = "login-header")
	WebElement loginScreen;

	@FindBy(id = "login_with_otp")
	WebElement loginWithOtpBtn;

	@FindBy(id = "Otp_IND")
	WebElement mobileNumberField;

	@FindBy(id = "get_otp")
	WebElement getOtpButton;

	@FindBy(id = "verify_otp")
	WebElement verifyOtpBtn;

	@FindBy(id = "navbar-header")
	WebElement proceedToAttentionScreen;

	@FindBy(id = "proceed-button")
	WebElement proceedButtonInAttentionPage;

	@FindBy(xpath = "//button[contains(@class,'inline-flex items-center justify-center')][2]")
	WebElement proceedButton;

	@FindBy(id = "mock-identity-verifier")
	WebElement eKycServiceProvider;

	@FindBy(id = "proceed-preview-button")
	WebElement proceedBtnInServiceProviderPage;

	@FindBy(id = "consent-button")
	WebElement termsAndConditionCheckBox;

	@FindBy(id = "proceed-tnc-button")
	WebElement proceedBtnInTandCPage;

	@FindBy(id = "proceed-preview-button")
	WebElement proceedBtnInCameraPreviewPage;

	@FindBy(xpath = "//div[@class='divide-y']/ul")
	List<WebElement> voluntaryClaimItems;

	@FindBy(xpath = "//label[@for='voluntary_claims']")
	WebElement voluntaryClaimsMasterToggle;

	@FindBy(xpath = "//input[@type='checkbox' and contains(@class, 'sr-only peer')]")
	List<WebElement> voluntaryClaimsSubToggles;

	@FindBy(xpath = "//input[@id='voluntary_claims']")
	WebElement voluntaryClaimsMasterCheckbox;

	@FindBy(id = "language_selection")
	WebElement languageDropdown;

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

	@FindBy(id = "vid")
	WebElement uinOrVidOption;

	@FindBy(id = "Otp_vid")
	WebElement uinOrVidTextField;

	@FindBy(id = "error-banner-message")
	WebElement invalidUinError;

	@FindBy(xpath = "//div[@class='pincode-input-container']/input")
	List<WebElement> otpInputFields;

	@FindBy(xpath = "//span[@class='w-full flex justify-center mt-6']")
	WebElement otpTimerText;

	@FindBy(id = "error-banner-message")
	WebElement invalidOtpError;

	@FindBy(id = "error-banner")
	WebElement unableToSendOtpError;

	@FindBy(id = "back-button")
	WebElement backButton;

	@FindBy(id = "continue")
	WebElement allowButton;

	@FindBy(xpath = "//img[@class='object-contain client-logo-size client-logo-shadow rounded-[25px] border-[0.1px] border-white']")
	WebElement relyingPartyLogo;

	@FindBy(xpath = "//img[@class='object-contain brand-only-logo client-logo-size']")
	WebElement imageOfEsignetLogo;

	@FindBy(id = "show-more-options")
	List<WebElement> moreWaysToSignIn;

	@FindBy(id = "hi1")
	WebElement hindiLanguage;

	@FindBy(id = "ar2")
	WebElement arabicLanguage;

	@FindBy(xpath = "//div[@class='h-screen']")
	WebElement rootContainer;

	@FindBy(id = "login_with_otp")
	WebElement loginWithOtpInHindi;

	@FindBy(xpath = "//button[@id='essential_claims_tooltip']/following::div[@class='divide-y'][1]//label")
	private List<WebElement> essentialClaims;

	@FindBy(xpath = "//button[@id='voluntary_claims_tooltip']/following::div[@class='divide-y'][1]//label")
	private List<WebElement> voluntaryClaims;

	@FindBy(xpath = "//button[contains(@class,'flex items-center px-4')]")
	WebElement profileDropdown;

	@FindBy(xpath = "(//div[@class='divide-y'])[1]/ul/li")
	List<WebElement> mandatoryClaimsElements;

	@FindBy(xpath = "(//div[@class='divide-y'])[2]/ul/li")
	List<WebElement> voluntaryClaimsElements;

	@FindBy(xpath = "//div[@class=' css-1dimb5e-singleValue']")
	WebElement selectedLanguageDropdown;

	@FindBy(id = "language_selection")
	WebElement languageSelection;

	@FindBy(id = "continue")
	WebElement allowButtonInConsentScreen;

	public void clickOnSignInWIthEsignet() {
		clickOnElement(signInWithEsignet);
		setAuthorizeUrl(driver.getCurrentUrl());
	}

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo);
	}

	public void clickOnLoginWithInji() {
		clickOnElement(loginWithInji);
	}

	public String getCurrentLanguage() {
		return languageSelection.getText().trim();
	}

	public boolean isLoginScreenDisplayed() {
		return isElementVisible(loginScreen);
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

	public void enterRegisteredMobileNumber(String number) {
		mobileNumberField.clear();
		enterText(mobileNumberField, number);
	}

	public boolean isBothLogoDisplayed() {
		return relyingPartyLogo.isDisplayed() && imageOfEsignetLogo.isDisplayed();
	}

	public void clickOnLoginWithOtp() {
		clickOnElement(loginWithOtpBtn);
	}

	public void enterOtp(String otp) {
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

	public void clickOnGetOtp() {
		clickOnElement(getOtpButton);
	}

	public boolean isConsentScreenVisible() {
		return isElementVisible(allowButton);
	}

	public boolean isVoluntaryClaimsMasterToggleVisible() {
		return voluntaryClaimItems.size() > 1 && isElementVisible(voluntaryClaimsMasterToggle);
	}

	public WebElement getVoluntaryClaimsMasterToggle() {
		return voluntaryClaimsMasterToggle;
	}

	public List<WebElement> getVoluntaryClaimsSubToggles() {
		return voluntaryClaimsSubToggles;
	}

	public void enableVoluntaryClaimsMasterToggle() {
		if (!voluntaryClaimsMasterToggle.isSelected()) {
			voluntaryClaimsMasterToggle.click();
		}
	}

	public void disableVoluntaryClaimsMasterToggle() {
		voluntaryClaimsMasterToggle.click();
	}

	public boolean isVoluntaryClaimsMasterToggleSelected() {
		return voluntaryClaimsMasterCheckbox.isSelected();
	}

	public boolean isLanguageDropdownDisplayed() {
		return isElementVisible(languageDropdown);
	}

	public void clickOnLanguageDropdown() {
		clickOnElement(languageDropdown);
	}

	public void clickOnHindiLanguage() {
		clickOnElement(hindiLanguage);
	}

	public void clickOnArabicLanguage() {
		clickOnElement(arabicLanguage);
	}

	public String getPageDirection() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
		wait.until(ExpectedConditions.attributeToBe(rootContainer, "dir", "rtl"));
		return rootContainer.getAttribute("dir");
	}

	public boolean isSelectedLanguageDisplayed() {
		return isElementVisible(loginWithOtpInHindi);
	}

	public boolean isLoginWithBiometicDisplayed() {
		return isElementVisible(loginWithBiometricBtn);
	}

	public boolean isLoginWithInjiDisplayed() {
		return isElementVisible(loginWithInjiBtn);
	}

	public boolean isLoginWithPasswordDisplayed() {
		return isElementVisible(loginWithPasswordBtn);
	}

	public void clickOnUinOrVidOption() {
		clickOnElement(uinOrVidOption);
	}

	public String getUinOrVidPlaceholder() {
		return getElementAttribute(uinOrVidTextField, "placeholder");
	}

	public void enterUinOrVid(String uin) {
		uinOrVidTextField.clear();
		enterText(uinOrVidTextField, uin);
	}

	public boolean isGetOtpButtonEnabled() {
		return isButtonEnabled(getOtpButton);
	}

	public boolean isGetOtpButtonDisplayed() {
		return isElementVisible(getOtpButton);
	}

	public boolean isInvalidIdErrorDisplayed() {
		return isElementVisible(invalidUinError);
	}

	public boolean isNavigatedToOtpPage() {
		return isElementVisible(verifyOtpBtn);
	}

	public boolean isVerifyOtpButtonEnabled() {
		return isButtonEnabled(verifyOtpBtn);
	}

	public void clickOnVerifyButton() {
		clickOnElement(verifyOtpBtn);
	}

	public int getOtpTimerValue() {
		String text = otpTimerText.getText().trim();
		String time = text.substring(text.lastIndexOf(" ") + 1);
		String[] parts = time.split(":");
		return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
	}

	public boolean isInvalidOtpErrorDisplayed() {
		return isElementVisible(invalidOtpError);
	}

	public boolean isUnableToSendOtpErrorDisplayed() {
		return isElementVisible(unableToSendOtpError);
	}

	public void clickOnBackButton() {
		clickOnElement(backButton);
	}

	public String getVoluntaryClaimsTooltipText() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
		WebElement icon = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("voluntary_claims_tooltip")));
		new Actions(driver).moveToElement(icon).perform();
		WebElement tooltip = wait.until(
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[contains(@class,'react-tooltip')]")));
		return tooltip.getText();
	}

	public void toggleVoluntaryClaim(String claimName, boolean enable) {
		String normalized = claimName.equalsIgnoreCase("fullname") ? "name" : claimName;
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
		clickOnElement(allowButtonInConsentScreen);
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

	public void clickOnProfileDropdown() {
		clickOnElement(profileDropdown);
	}

	public String getSelectedLanguageFromDropdown() {
		return selectedLanguageDropdown.getText().trim();
	}

	public List<String> getDisplayedMandatoryClaims() {
		List<String> claims = new ArrayList<>();
		String localizedRequired = ResourceBundleLoader.get("consent.required").trim().toLowerCase();

		for (WebElement element : mandatoryClaimsElements) {
			String text = element.getText().trim().toLowerCase();
			if (text.contains(localizedRequired)) {
				text = text.replace(localizedRequired, "").trim();
			}

			claims.add(text);
		}
		return claims;
	}

	public List<String> getDisplayedVoluntaryClaims() {
		List<String> claims = new ArrayList<>();
		for (WebElement element : voluntaryClaimsElements) {
			claims.add(element.getText().trim().toLowerCase());
		}
		return claims;
	}

	public Map<String, WebElement> getAcrToElementMap() {
		Map<String, WebElement> map = new HashMap<>();
		map.put("mosip:idp:acr:password", loginWithPasswordBtn);
		map.put("mosip:idp:acr:generated-code", loginWithOtpBtn);
		map.put("mosip:idp:acr:biometrics", loginWithBiometricBtn);
		map.put("mosip:idp:acr:linked-wallet", loginWithInjiBtn);
		return map;
	}

	public boolean isOnAttentionScreen() {
		return proceedToAttentionScreen.isDisplayed();
	}

	public void clickOnProceedButtonInAttentionPage() {
		clickOnElement(proceedButtonInAttentionPage);
	}

	public void clickOnProceedButton() {
		clickOnElement(proceedButton);
	}

	public void clickOnMockIdentifyVerifier() {
		clickOnElement(eKycServiceProvider);
	}

	public void clickOnProceedButtonInServiceProviderPage() {
		clickOnElement(proceedBtnInServiceProviderPage);
	}

	public void checkTermsAndCondition() {
		if (!termsAndConditionCheckBox.isSelected()) {
			clickOnElement(termsAndConditionCheckBox);
		}
	}

	public void clickOnProceedButtonInTermsAndConditionPage() {
		clickOnElement(proceedBtnInTandCPage);
	}

	public void clickOnProceedButtonInCameraPreviewPage() {
		clickWhenClickable(proceedBtnInCameraPreviewPage);
	}

	public void waitUntilLivenessCheckCompletes() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(50));
		wait.until(ExpectedConditions.visibilityOf(allowButtonInConsentScreen));
	}

	private void clickWhenClickable(WebElement element) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		WebElement stableElement = wait
				.until(ExpectedConditions.refreshed(ExpectedConditions.elementToBeClickable(element)));
		stableElement.click();
	}

}
