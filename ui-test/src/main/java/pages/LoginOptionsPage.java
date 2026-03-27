package pages;

import base.BasePage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class LoginOptionsPage extends BasePage {

	public LoginOptionsPage(WebDriver driver) {
		super(driver);
	}

	@FindBy(id = "signup-url-button")
	WebElement loginButton;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(id = "login_with_walletname")
	WebElement loginWithInji;

	@FindBy(id = "language_selection")
	WebElement languageDropdown;

	@FindBy(id = "hi1")
	WebElement hindiLanguage;

	@FindBy(id = "login_with_otp")
	WebElement loginWithOtpBtn;

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

	@FindBy(id = "show-more-options")
	List<WebElement> moreWaysToSignIn;

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo,"Verified is logo displayed");
	}

	public void clickOnLoginWithInji() {
		clickOnElement(loginWithInji,"Clicked on login with inji");
	}

	public boolean isLanguageDropdownDisplayed() {
		return isElementVisible(languageDropdown,"Verified language dropdown is visible");
	}

	public void clickOnLanguageDropdown() {
		clickOnElement(languageDropdown,"Clicked on language dropdown");
	}

	public void clickOnHindiLanguage() {
		clickOnElement(hindiLanguage,"Selected hindi language from dropdown");
	}

	public boolean isSelectedLanguageDisplayed() {
		return isElementVisible(loginWithOtpBtn,"Verified selected language displayed");
	}

	public boolean isLoginWithBiometicDisplayed() {
		return isElementVisible(loginWithBiometricBtn,"Verified login with biometric button is displayed");
	}

	public boolean isLoginWithInjiDisplayed() {
		return isElementVisible(loginWithInjiBtn,"Verified login with inji button is displayed");
	}

	public boolean isLoginWithPasswordDisplayed() {
		return isElementVisible(loginWithPasswordBtn,"Verified login with password button is displayed");
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

	public Map<String, WebElement> getAcrToElementMap() {
		Map<String, WebElement> map = new HashMap<>();
		map.put("PWD", loginWithPasswordBtn);
		map.put("OTP", loginWithOtpBtn);
		map.put("BIO", loginWithBiometricBtn);
		map.put("WLA", loginWithInjiBtn);
		map.put("PIN", loginWithPinBtn);
		map.put("KBI", loginWithKbiBtn);
		return map;
	}

	public void selectLanguage(String language) {
		WebElement langOption = driver
				.findElement(By.xpath("//div[@role='menuitem' and normalize-space()='" + language + "']"));

		langOption.click();
	}

	public boolean isUILanguageChanged(String text) {
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
	    wait.until(ExpectedConditions.textToBePresentInElement(loginButton, text));
	    return loginButton.getText().contains(text);
	}
	
	public WebElement getLoginWithOtpButton() {
		return loginWithOtpBtn;
	}

}