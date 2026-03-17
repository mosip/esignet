package pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;
import utils.ClaimsUtil;

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