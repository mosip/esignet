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

	public boolean isEkycProcessStepsScreenLabelDisplayed() {
		return isElementVisible(ekycProcessStepsScreenLabel, "Verified eKyc Screen is displayed");
	}

	public boolean isEkycStep1TitleChooseEkycProviderDisplayed() {
		return isElementVisible(eKycStep1Title, "Verified the title step 1 in eKyc screen");
	}

	public boolean isEkycStep1SubtitleDisplayed() {
		return isElementVisible(eKycStep1Subtitle, "Verified the sub-title step 1 in eKyc screen");
	}

	public boolean isEkycStep2TitleTermsAndConditionsDisplayed() {
		return isElementVisible(eKycStep2Title, "Verified the title step 2 in eKyc screen");
	}

	public boolean isEkycStep2SubtitleDisplayed() {
		return isElementVisible(eKycStep2Subtitle, "Verified the sub-title step 2 in ekyc screen");
	}
}