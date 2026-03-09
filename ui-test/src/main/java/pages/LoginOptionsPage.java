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
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class LoginOptionsPage extends BasePage {

	public LoginOptionsPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
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
		return isElementVisible(brandLogo);
	}

	public void clickOnLoginWithInji() {
		clickOnElement(loginWithInji);
	}

	public boolean isLanguageDropdownDisplayed() {
		return isElementVisible(languageDropdown);
	}

	public void clickOnLanguageDropdown() {
		clickOnElement(languageDropdown);
	}

	public void clickOnHindiLanguage() {
		clickOnElement(hindiLanguage);
	}

	public boolean isSelectedLanguageDisplayed() {
		return isElementVisible(loginWithOtpBtn);
	}

	public boolean isLoginWithBiometicDisplayed() {
		return isElementVisible(loginWithBiometricBtn);
	}

	public boolean isLoginWithInjiDisplayed() {
		return isElementVisible(loginWithInjiBtn);
	}

	public boolean isLoginWithPasswordDisplayed() {
		return isElementVisible(loginWithPasswordBtn);
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

}