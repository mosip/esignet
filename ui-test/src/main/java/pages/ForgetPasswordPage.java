package pages;

import java.util.NoSuchElementException;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import base.BasePage;

public class ForgetPasswordPage extends BasePage {

	public ForgetPasswordPage(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(xpath = "//div[contains(text(), 'Login with Password')]")
	WebElement loginWithPassword;

	@FindBy(id = "forgot-password-hyperlink")
	WebElement forgetPasswordLink;

	@FindBy(id = "sign-in-with-esignet")
	WebElement signInWithEsignet;

	@FindBy(xpath = "//img[@class='brand-logo']")
	WebElement brandLogo;

	@FindBy(xpath = "//span[@class='flex self-center border-r-[1px] border-input px-3 text-muted-foreground/60']")
	WebElement phonePrefix;

	@FindBy(id = "phone_input")
	WebElement phoneInput;

	@FindBy(xpath = "//span[text()='+855']")
	WebElement countryCodeSpan;
	
	@FindBy(xpath = "//div/p[@id=':r4:-form-item-message']")
	WebElement phoneErrorForInvalidValue;
	
	@FindBy(xpath = "//div[@class='text-center text-[26px] font-semibold tracking-normal']")
	WebElement forgetPasswordHeadning;
	
	@FindBy(id = "keyboard_backspace_FILL0_wght400_GRAD0_opsz48")
	WebElement backButtonOnForgePassword;

	@FindBy(xpath = "//div[@class='text-center text-gray-500']")
	WebElement forgetPasswordSubHeadning;
	
	@FindBy(xpath = "//label[normalize-space(text())='Username']")
	WebElement userNameLabel;

	@FindBy(xpath = "//label[normalize-space(text())='Full Name in Khmer']")
	WebElement fullNameLabel;
	
	@FindBy(id = "continue-button")
	WebElement continueButton;
	
	@FindBy(id = "language-select-button")
	WebElement LangSelectionButton;
	
	@FindBy(xpath = "//footer//span[text()='Powered by']")
	WebElement poweredByText;

	@FindBy(xpath = "//footer//img[@class='footer-brand-logo' and @alt='eSignet Signup']")
	WebElement footerLogo;
	
	@FindBy(id = "fullname") 
	WebElement fullNameInput;

	@FindBy(xpath = "//p[@id=':r5:-form-item-message']")
	WebElement fullNameError;	

	
	public void clickOnLoginWithPassword() {
		clickOnElement(loginWithPassword);
	}

	public boolean isforgetPasswordLinkDisplayed() {
		return isElementVisible(forgetPasswordLink);
	}

	public void clickOnForgetPasswordLink() {
		clickOnElement(forgetPasswordLink);
	}

	public void clickOnSignInWIthEsignet() {
		clickOnElement(signInWithEsignet);
	}

	public boolean isLogoDisplayed() {
		return isElementVisible(brandLogo);
	}

	public boolean isRedirectedToResetPasswordPage() {
		String currentUrl = driver.getCurrentUrl();
		return currentUrl.contains("https://signup.es-qa.mosip.net/reset-password");
	}

	public boolean isphonePrefixDisplayed() {
		return isElementVisible(phonePrefix);
	}

	public boolean isWaterMarkDisplayed() {
		return getElementAttribute(phoneInput, "placeholder").equals("Enter 8-9 digit mobile number");
	}

	public boolean isCountryCodeNonEditable() {
		return isElementVisible(countryCodeSpan) && !getElementTagName(countryCodeSpan).equalsIgnoreCase("input");
	}

	public boolean isPhoneErrorVisible() {	
		return isElementVisible(phoneErrorForInvalidValue);
	}

	public void enterPhoneNumber(String number) {
		enterText(phoneInput, number);
	}

	public void triggerPhoneValidation() {
		clickOnElement(countryCodeSpan);		
	}
	
	
	public boolean isForgetPasswordHeadningVisible() {	
		return isElementVisible(forgetPasswordHeadning);
	}

	public boolean isBackButtonOnForgePasswordVisible() {	
		return isElementVisible(backButtonOnForgePassword);
	}

	public boolean isForgetPasswordSubHeadningVisible() {	
		return isElementVisible(forgetPasswordSubHeadning);
	}
	
	public boolean isUserNameLabelVisible() {	
		return isElementVisible(userNameLabel);
	}

	public boolean isFullNameLabelVisible() {	
		return isElementVisible(fullNameLabel);
	}
	
	public boolean isContinueButtonVisible() {	
		return isElementVisible(continueButton);
	}
	
	public boolean isLangSelectionButtonVisible() {	
		return isElementVisible(LangSelectionButton);
	}

	public boolean isFooterPoweredByVisible() {
	    return isElementVisible(poweredByText) && isElementVisible(footerLogo);
	}
	
	public String getEnteredPhoneNumber() {
		return getElementAttribute(phoneInput,"value");
	}

	public boolean isContinueButtonDisabled() {
		return !isButtonEnabled(continueButton);
	}

	public void enterFullName(String name) {
		enterText(fullNameInput, name);
	}

	public boolean isFullNameErrorVisible() {
	    try {
	        return isElementVisible(fullNameError);
	    } catch (NoSuchElementException e) {
	        return false; 
	    }
	}	

	public boolean isFullNameErrorPresent() {
	    return driver.findElements(By.xpath("//p[@id=':r5:-form-item-message']")).size() > 0;
	}


	public String getEnteredFullName() {
		return getElementAttribute(fullNameInput,"value");
	}
	
	public boolean isContinueButtonEnabled() {
		return isButtonEnabled(continueButton);
	}		
	
}