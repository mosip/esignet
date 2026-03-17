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
		return isElementVisible(ekycProcessStepsScreenLabel);
	}

	public boolean isEkycStep1TitleChooseEkycProvider() {
		return isElementVisible(eKycStep1Title);
	}

	public boolean isEkycStep1Subtitle() {
		return isElementVisible(eKycStep1Subtitle);
	}

	public boolean isEkycStep2TitleTermsAndConditions() {
		return isElementVisible(eKycStep2Title);
	}

	public boolean isEkycStep2Subtitle() {
		return isElementVisible(eKycStep2Subtitle);
	}
}