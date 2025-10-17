package pages;

import base.BasePage;
import utils.EsignetConfigManager;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class SmtpPage extends BasePage {

	public SmtpPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(xpath = "//li[.//i[contains(@class,'fa-envelope')] and .//div[contains(@class,'flex text-gray-600 font-semibold')]]")
	WebElement emailNotification;
	
	@FindBy(xpath = "//li[.//i[contains(@class,'fa-commenting')] and .//div[contains(@class,'flex text-gray-600 font-semibold')]]")
	WebElement mobileNumberNotification;

	public void navigateToSmtpUrl() {
		driver.get(EsignetConfigManager.getSmtpUrl());
	}

	public void navigateToHealthPortalUrl() {
		driver.get(EsignetConfigManager.getHealthPortalUrl());
	}

	public boolean isEmailOtpNotificationReceived() {
		return isElementVisible(emailNotification);
	}

	public boolean isOtpReceivedForMobileNumber() {
		return isElementVisible(mobileNumberNotification);
	}
	
	public boolean isOtpReceivedForMobileNumberAndEmail() {
		return isElementVisible(emailNotification) && isElementVisible(mobileNumberNotification);
	}

}