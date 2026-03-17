package pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import base.BasePage;

public class EkycPage extends BasePage {

	public EkycPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(id = "tnc-header")
	WebElement ekycProcessStepsScreenLabel;

	@FindBy(id = "step-label-0")
	WebElement eKycStep1Title;

	@FindBy(id = "step-description-0")
	WebElement eKycStep1Subtitle;

	@FindBy(id = "step-label-1")
	WebElement eKycStep2Title;

	@FindBy(id = "step-description-1")
	WebElement eKycStep2Subtitle;

	@FindBy(id = "step-label-2")
	WebElement eKycStep3Title;

	@FindBy(id = "step-description-2")
	WebElement eKycStep3Subtitle;

	@FindBy(id = "step-label-3")
	WebElement eKycStep4Title;

	@FindBy(id = "step-description-3")
	WebElement eKycStep4Subtitle;

	@FindBy(id = "step-label-4")
	WebElement eKycStep5Title;

	@FindBy(id = "step-description-4")
	WebElement eKycStep5Subtitle;

	@FindBy(xpath = "//button[contains(@class,'inline-flex items-center justify-center')][1]")
	WebElement cancelButton;

	@FindBy(xpath = "//h2[@class='font-semibold flex flex-col items-center justify-center gap-y-4 text-[1.5em]']")
	WebElement ekycWarningPopupHeader;

	@FindBy(xpath = "//p[@class='text-center text-muted-dark-gray text-md']")
	WebElement ekycWarningPopupMessage;

	@FindBy(id = "stay-button")
	WebElement ekycWarningPopupstayButton;

	@FindBy(id = "dismiss-button")
	WebElement ekycWarningPopupdiscontinueButton;

	@FindBy(id = "sign-in-with-esignet")
	WebElement relyingPartyloginPage;

	@FindBy(id = "1")
	WebElement loginPageErrorMessage;

	@FindBy(xpath = "//*[contains(@class,'SignInWithEsignet-module_textbox__k2CkO')]")
	WebElement signInWithEsignetButton;

	@FindBy(xpath = "//button[contains(@class,'inline-flex items-center justify-center')][2]")
	WebElement proceedButton;

	@FindBy(id = "kyc-provider-header")
	WebElement ekycServiceProvidersHeader;

	public boolean isEkycProcessStepsScreenLabelDisplayed() {
		return isElementVisible(ekycProcessStepsScreenLabel);
	}

	public boolean isEkycStep1TitleChooseEkycProviderDisplayed() {
		return isElementVisible(eKycStep1Title);
	}

	public boolean isEkycStep1SubtitleDisplayed() {
		return isElementVisible(eKycStep1Subtitle);
	}

	public boolean isEkycStep2TitleTermsAndConditionsDisplayed() {
		return isElementVisible(eKycStep2Title);
	}

	public boolean isEkycStep2SubtitleDisplayed() {
		return isElementVisible(eKycStep2Subtitle);
	}

	public boolean isEkycStep3TitlePreVerificationGuideDisplayed() {
		return isElementVisible(eKycStep3Title);
	}

	public boolean isEKycStep3SubtitleDisplayed() {
		return isElementVisible(eKycStep3Subtitle);
	}

	public boolean isEkycStep4TitleIdentityVerificationDisplayed() {
		return isElementVisible(eKycStep4Title);
	}

	public boolean isEKycStep4SubtitleDisplayed() {
		return isElementVisible(eKycStep4Subtitle);
	}

	public boolean isEkycStep5TitleReviewConsentDisplayed() {
		return isElementVisible(eKycStep5Title);
	}

	public boolean isEKycStep5SubtitleDisplayed() {
		return isElementVisible(eKycStep5Subtitle);
	}

	public boolean isCancelButtonVisible() {
		return isElementVisible(cancelButton);
	}

	public void clickOnCancelButton() {
		clickOnElement(cancelButton);
	}

	public boolean isCancelWarningPopupDisplayed() {
		return isElementVisible(ekycWarningPopupstayButton);
	}

	public boolean isWarningPopupHeaderDisplayed() {
		return isElementVisible(ekycWarningPopupHeader);
	}

	public boolean isWarningPopupMessageDisplayed() {
		return isElementVisible(ekycWarningPopupMessage);
	}

	public boolean isStayButtonVisible() {
		return isElementVisible(ekycWarningPopupstayButton);
	}

	public boolean isDiscontinueButtonVisible() {
		return isElementVisible(ekycWarningPopupdiscontinueButton);
	}

	public void clickOnStayButton() {
		clickOnElement(ekycWarningPopupstayButton);
	}

	public boolean isEkyScreenVisible() {
		return isElementVisible(ekycProcessStepsScreenLabel);
	}

	public void clickOnDiscontinueButton() {
		clickOnElement(ekycWarningPopupdiscontinueButton);
	}

	public boolean isLoginPageDisplayed() {
		return isElementVisible(relyingPartyloginPage);
	}

	public boolean isErrorMessageTextDisplayed() {
		return isElementVisible(loginPageErrorMessage);
	}

	public void clickOnSignInWithEsignetButton() {
		clickOnElement(signInWithEsignetButton);
	}

	public boolean isProceedButtonVisible() {
		return isElementVisible(proceedButton);
	}

	public void clickOnProceedButton() {
		clickOnElement(proceedButton);
	}

	public boolean isEkycServiceProviderScreenVisible() {
		return isElementVisible(ekycServiceProvidersHeader);
	}
}