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
	WebElement step1Title;

	@FindBy(id = "step-description-0")
	WebElement step1Subtitle;

	@FindBy(id = "step-label-1")
	WebElement step2Title;

	@FindBy(id = "step-description-1")
	WebElement step2Subtitle;
	
	public boolean isEkycProcessStepsScreenLabelDisplayed() {
		return isElementVisible(ekycProcessStepsScreenLabel);
	}

	public boolean isVerifyStep1TitleChooseEkycProvider() {
		return isElementVisible(step1Title);
	}

	public boolean isVerifyStep1Subtitle() {
		return isElementVisible(step1Subtitle);
	}

	public boolean isVerifyStep2TitleChooseEkycProvider() {
		return isElementVisible(step2Title);
	}

	public boolean isVerifyStep2Subtitle() {
		return isElementVisible(step2Subtitle);
	}
}