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

	@FindBy(xpath = "//p[@class='text-gray-700 mb-2 mr-3 ml-4']")
	WebElement otpNotification;

	@FindBy(xpath = "//p[@class='text-gray-700 mb-2 mr-3 ml-4']")
	WebElement registrationSuccessfullNotification;

	@FindBy(xpath = "//p[@class='text-gray-700 mb-2 mr-3 ml-4']")
	WebElement passwordResetSuccessfullNotification;

	public void navigateToSmtpUrl() {
		driver.get(EsignetConfigManager.getSmtpUrl());
	}

	public void navigateToHealthPortalUrl() {
		driver.get(EsignetConfigManager.getHealthPortalUrl());
	}

	public String getOtpNotificationText() {
		return otpNotification.getText();
	}

	public boolean isNotificationReceivedInEnglish() {
		return isElementVisible(otpNotification);
	}

	public boolean isNotificationReceivedInKhmer() {
		return isElementVisible(otpNotification);
	}

	public boolean isSuccessfullNotificationReceivedInEnglish() {
		return isElementVisible(registrationSuccessfullNotification);
	}

	public boolean isSuccessfullNotificationReceivedInKhmer() {
		return isElementVisible(registrationSuccessfullNotification);
	}

	public boolean isPasswordResetSuccessfullNotificationReceivedInEnglish() {
		return isElementVisible(passwordResetSuccessfullNotification);
	}

	public boolean isPasswordResetSuccessfullNotificationReceivedInKhmer() {
		return isElementVisible(passwordResetSuccessfullNotification);
	}

}
