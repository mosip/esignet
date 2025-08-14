package pages;

import base.BasePage;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class LoginOptionsPage extends BasePage {

	public LoginOptionsPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "sign-in-with-esignet")
	WebElement signInWithEsignet;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(id = "signup-url-button")
	WebElement signUpWithUnifiedLogin;

	@FindBy(id = "login_with_pwd")
	WebElement loginWithPasswordButton;

	@FindBy(id = "Password_KHM")
	WebElement registeredMobileNumber;

	@FindBy(id = "Password_mobile")
	WebElement passwordField;

	@FindBy(id = "verify_password")
	WebElement loginButton;

	@FindBy(id = "error-banner-message")
	WebElement invalidNumberError;

	@FindBy(id = "password-eye")
	WebElement passwordEyeIcon;

	@FindBy(xpath = "//span[contains(text(),'Please Enter Valid Password')]")
	WebElement invalidPasswordError;

	@FindBy(id = "error-banner-message")
	WebElement invalidUsernameOrPasswordError;

	@FindBy(id = "error-close-button")
	WebElement errorCloseIcon;

	@FindBy(id = "navbar-header")
	WebElement proceedToAttentionScreen;

	@FindBy(id = "login-subheader")
	WebElement userIsOnLogInEsignetPage;

	@FindBy(id = "proceed-button")
	WebElement proceedButtonInAttentionPage;

	@FindBy(xpath = "//button[@class='inline-flex items-center justify-center whitespace-nowrap text-lg rounded-lg ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:bg-muted disabled:pointer-events-none bg-primary text-primary-foreground hover:bg-primary/90 h-[62px] py-2 px-[6rem] font-semibold sm:w-full sm:p-4']")
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

	@FindBy(xpath = "//p[@class='text-[#4E4E4E] font-semibold']")
	WebElement consentScreen;

	@FindBy(xpath = "//div[@class='text-center']")
	WebElement headerScreenOnConsentPage;

	@FindBy(xpath = "//img[@class='object-contain client-logo-size']")
	WebElement imageOfHealthCareDesign;

	@FindBy(xpath = "//img[@class='object-contain brand-only-logo client-logo-size']")
	WebElement imageOfEsignetLogo;

	@FindBy(id = "essential_claims_tooltip")
	WebElement essentialIicon;

	@FindBy(xpath = "//div[@class='divide-y']")
	WebElement lookForEssentialClaims;

	@FindBy(id = "voluntary_claims_tooltip")
	WebElement voluntarIicon;

	@FindBy(xpath = "//div[@class='divide-y']")
	WebElement lookForVoluntarClaims;

	@FindBy(id = "continue")
	WebElement allowButtonInConsentScreen;

	@FindBy(id = "cancel")
	WebElement cancelButtonInConsentScreen;

	@FindBy(xpath = "//div[@class='react-tooltip styles-module_tooltip__mnnfp styles-module_dark__xNqje md:w-3/6 lg:max-w-sm m-0 md:m-auto styles-module_show__2NboJ']")
	WebElement essentialClaimToolTipMessege;

	@FindBy(xpath = "//label[@class='text-sm text-[#01070DD5]']")
	WebElement phoneNumberFieldUnderEssentialClaims;

	@FindBy(xpath = "//div[@class='relative text-center text-dark font-semibold text-xl text-[#2B3840] mt-4']")
	WebElement cancelWarningHeader;

	@FindBy(xpath = "//p[@class='text-base text-[#707070]']")
	WebElement cancelWarningMessage;

	@FindBy(xpath = "//button[@class='flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 secondary-button']")
	WebElement discontinueButton;

	@FindBy(xpath = "//button[@class='flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 primary-button']")
	WebElement stayButton;

	@FindBy(xpath = "//label[@labelfor='voluntary_claims']")
	WebElement voluntaryClaimMasterToggle;

	@FindBy(xpath = "//span[@class='self-center text-2xl font-semibold whitespace-nowrap']")
	WebElement welcomePageOfRelyingParty;

	public void clickOnSignInWIthEsignet() {
		clickOnElement(signInWithEsignet);
	}

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo);
	}

	public boolean isSignUpWithUnifiedLoginOptionDisplayed() {
		return isElementVisible(signUpWithUnifiedLogin);
	}

	public void clickOnSignUpWithUnifiedLogin() {
		clickOnElement(signUpWithUnifiedLogin);
	}

	public boolean isLoginEsignetPageDisplayed() {
		return userIsOnLogInEsignetPage.isDisplayed();
	}

	public void enterRegisteredMobileNumber(String number) {
		registeredMobileNumber.clear();
		enterText(registeredMobileNumber, number);
	}

	public void enterPassword(String Password) {
		clearField(passwordField);
		enterText(passwordField, Password);
	}

	public void clickOnLoginButton() {
		loginButton.click();
	}
	/*
	 * public void enterOtpToLogin(String otp) { for (WebElement field : otpField) {
	 * field.click(); field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
	 * field.sendKeys(Keys.BACK_SPACE); } for (int i = 0; i < otp.length(); i++) {
	 * WebElement field = otpField.get(i); field.click();
	 * field.sendKeys(String.valueOf(otp.charAt(i))); } }
	 */

	public void clickOnLoginWithPasswordOption() {
		loginWithPasswordButton.click();
	}

	public boolean isInvalidNumberErrorDisplayed() {
		return invalidNumberError.isDisplayed();
	}

	public void userTaboutOfPasswordField() {
		clickOnElement(passwordEyeIcon);
	}

	public boolean isInvalidPasswordErrorDisplayed() {
		return invalidPasswordError.isDisplayed();
	}

	public boolean isInvalidUsernameOrPasswordErrorDisplayed() {
		return invalidUsernameOrPasswordError.isDisplayed();
	}

	public boolean isLoginButtonEnabled() {
		return isButtonEnabled(loginButton);
	}

	public void clickOnErrorCloseIcon() {
		clickOnElement(errorCloseIcon);
	}

	public void verifyErrorDisappearsAfterClose() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
		wait.until(ExpectedConditions.invisibilityOf(invalidUsernameOrPasswordError));
	}

	public void verifyErrorMessageDisappearsAfterTenSeconds() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(12));
		wait.until(ExpectedConditions.invisibilityOf(invalidUsernameOrPasswordError));
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

	public void checkTermsAndConditions() {
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

	public boolean isConsentSceenDisplayed() {
		return headerScreenOnConsentPage.isDisplayed();
	}

	public boolean areLogosDisplayed() {
		return imageOfHealthCareDesign.isDisplayed() && imageOfEsignetLogo.isDisplayed();
	}

	public boolean isEssentialIconDisplayed() {
		return essentialIicon.isDisplayed();
	}

	public boolean isEssentialClaimsDisplayed() {
		return lookForEssentialClaims.isDisplayed();
	}

	public boolean isVoluntaryClaimsIconDisplayed() {
		return voluntarIicon.isDisplayed();
	}

	public boolean isVoluntaryClaimsDisplayed() {
		return lookForVoluntarClaims.isDisplayed();
	}

	public boolean isAllowButtonInConsentScreenVisible() {
		return allowButtonInConsentScreen.isDisplayed();
	}

	public boolean isCancelButtonInConsentScreenVisible() {
		return cancelButtonInConsentScreen.isDisplayed();
	}

	public void clickOnEssentialTooltipIcon() {
		clickOnElement(essentialIicon);
	}

	public String getEssentialClaimsTooltipText() {
		Actions actions = new Actions(driver);
		actions.moveToElement(essentialIicon).perform();
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
		WebElement tooltip = wait.until(ExpectedConditions.visibilityOf(essentialClaimToolTipMessege));
		return tooltip.getText();
	}

	public boolean isPhoneNumberListedUnderEssentialClaims() {
		return phoneNumberFieldUnderEssentialClaims.isDisplayed();
	}

	public boolean isPhoneNumberFieldNonEditable() {
		String tagName = phoneNumberFieldUnderEssentialClaims.getTagName();
		return tagName.equalsIgnoreCase("label");
	}

	public void clickOnCancelBtnInConsentScreen() {
		clickOnElement(cancelButtonInConsentScreen);
	}

	public boolean isCancelWarningHeaderDisplayed() {
		return cancelWarningHeader.isDisplayed();
	}

	public boolean isCancelWarningMessageDisplayed() {
		return cancelWarningMessage.isDisplayed();
	}

	public boolean isStayButtonDisplayed() {
		return stayButton.isDisplayed();
	}

	public boolean isDiscontinueButtonDisplayed() {
		return discontinueButton.isDisplayed();
	}

	public void clickOnStayBtnInConsentScreen() {
		clickOnElement(stayButton);
	}

	public boolean isRetainsOnConsentSceenDisplayed() {
		return headerScreenOnConsentPage.isDisplayed();
	}

	public void clickOnDiscontinueButton() {
		clickOnElement(discontinueButton);
	}

	public boolean isHealthPortalPageDisplayed() {
		return signInWithEsignet.isDisplayed();
	}

	public void enableMasterToggleVoluntaryClaims() {
		if (!voluntaryClaimMasterToggle.isSelected()) {
			voluntaryClaimMasterToggle.click();
		}
	}

	public void clickOnAllowBtnInConsentScreen() {
		clickOnElement(allowButtonInConsentScreen);
	}

	public boolean isWelcomePageDisplayed() {
		return welcomePageOfRelyingParty.isDisplayed();
	}

}
