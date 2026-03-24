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

	@FindBy(xpath = "(//button[contains(@class,'inline-flex items-center justify-center')])[1]")
	WebElement cancelButton;

	@FindBy(xpath = "//h2[@class='font-semibold flex flex-col items-center justify-center gap-y-4 text-[1.5em]']")
	WebElement ekycWarningPopupHeader;

	@FindBy(xpath = "//p[@class='text-center text-muted-dark-gray text-md']")
	WebElement ekycWarningPopupMessage;

	@FindBy(id = "stay-button")
	WebElement ekycWarningPopupStayButton;

	@FindBy(id = "dismiss-button")
	WebElement ekycWarningPopupDiscontinueButton;

	@FindBy(id = "sign-in-with-esignet")
	WebElement relyingPartyLoginPage;

	@FindBy(id = "sign-in-with-esignet")
	WebElement signInWithEsignetButton;

	@FindBy(xpath = "(//button[contains(@class,'inline-flex items-center justify-center')])[2]")
	WebElement proceedButton;

	@FindBy(id = "kyc-provider-header")
	WebElement ekycServiceProvidersHeader;

	public boolean isEkycProcessStepsScreenLabelDisplayed() {
		return isElementVisible(ekycProcessStepsScreenLabel, "Verified eKyc screen is displayed");
	}

	public boolean isEkycStep1TitleChooseEkycProviderDisplayed() {
		return isElementVisible(eKycStep1Title, "Verified the title of step 1 in eKyc screen");
	}

	public boolean isEkycStep1SubtitleDisplayed() {
		return isElementVisible(eKycStep1Subtitle, "Verified the sub-title of step 1 in eKyc screen");
	}

	public boolean isEkycStep2TitleTermsAndConditionsDisplayed() {
		return isElementVisible(eKycStep2Title, "Verified the title of step 2 in eKyc screen");
	}

	public boolean isEkycStep2SubtitleDisplayed() {
		return isElementVisible(eKycStep2Subtitle, "Verified the sub-title of step 2 in ekyc screen");
	}

	public boolean isEkycStep3TitlePreVerificationGuideDisplayed() {
		return isElementVisible(eKycStep3Title, "Verified the title of step 3 in eKyc screen");
	}

	public boolean isEkycStep3SubtitleDisplayed() {
		return isElementVisible(eKycStep3Subtitle, "Verified the sub-title of step 3 in ekyc screen");
	}

	public boolean isEkycStep4TitleIdentityVerificationDisplayed() {
		return isElementVisible(eKycStep4Title, "Verified the title of step 4 in eKyc screen");
	}

	public boolean isEkycStep4SubtitleDisplayed() {
		return isElementVisible(eKycStep4Subtitle, "Verified the sub-title of step 4 in ekyc screen");
	}

	public boolean isEkycStep5TitleReviewConsentDisplayed() {
		return isElementVisible(eKycStep5Title, "Verified the title of step 5 in eKyc screen");
	}

	public boolean isEkycStep5SubtitleDisplayed() {
		return isElementVisible(eKycStep5Subtitle, "Verified the sub-title of step 5 in ekyc screen");
	}

	public boolean isCancelButtonVisible() {
		return isElementVisible(cancelButton, "Verified cancel button is displayed in eKyc process steps screen");
	}

	public void clickOnCancelButton() {
		clickOnElement(cancelButton, "Clicked on cancel button in eKyc process steps screen");
	}

	public boolean isCancelWarningPopupDisplayed() {
		return isElementVisible(ekycWarningPopupStayButton, "Verified cancel warning popup is displayed");
	}

	public boolean isWarningPopupHeaderDisplayed() {
		return isElementVisible(ekycWarningPopupHeader, "Verified warning popup header is displayed");
	}

	public boolean isWarningPopupMessageDisplayed() {
		return isElementVisible(ekycWarningPopupMessage, "Verified warning popup message is displayed");
	}

	public boolean isStayButtonVisible() {
		return isElementVisible(ekycWarningPopupStayButton, "Verified stay button is visible in warning popup");
	}

	public boolean isDiscontinueButtonVisible() {
		return isElementVisible(ekycWarningPopupDiscontinueButton,
				"Verified discontinue button is visible in warning popup");
	}

	public void clickOnStayButton() {
		clickOnElement(ekycWarningPopupStayButton, "Clicked on stay button in warning popup");
	}

	public boolean isEkyScreenVisible() {
		return isElementVisible(ekycProcessStepsScreenLabel, "Verified eKyc process Steps screen is visible");
	}

	public void clickOnDiscontinueButton() {
		clickOnElement(ekycWarningPopupDiscontinueButton, "Clicked on discontinue button in warning popup");
	}

	public boolean isLoginPageDisplayed() {
		return isElementVisible(relyingPartyLoginPage, "Verified Login page is displayed");
	}

	public void clickOnSignInWithEsignetButton() {
		clickOnElement(signInWithEsignetButton, "Clicked on sign in with eSignet button");
	}

	public boolean isProceedButtonVisible() {
		return isElementVisible(proceedButton, "Verified proceed button is visible");
	}

	public void clickOnProceedButton() {
		clickOnElement(proceedButton, "Clicked on proceed button");
	}

	public boolean isEkycServiceProviderScreenVisible() {
		return isElementVisible(ekycServiceProvidersHeader, "Verified eKyc service provider screen is visible");
	}
}