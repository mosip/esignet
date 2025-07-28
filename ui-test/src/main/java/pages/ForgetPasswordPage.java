package pages;

import java.util.NoSuchElementException;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
	
	 @FindBy(xpath = "//div[contains(text(),'Please enter 6-digit OTP received on your number')]")
	    WebElement otpInstructionText;

	    @FindBy(xpath = "//div[contains(text(),'Enter OTP')]")
	    WebElement enterOtpHeading;

	    @FindBy(xpath = "//div[@class='font-medium text-muted-dark-gray']")
	    WebElement partiallyMaskedPhone;

	    @FindBy(xpath = "//div[contains(text(),'You can resend the OTP in')]")
	    WebElement resendOtpCountdownText;

	    @FindBy(id = "resend-otp-button")
	    WebElement resendOtpButton;
	    
	    @FindBy(xpath = "//div[@class='font-medium text-muted-dark-gray']")
	    private WebElement maskedPhone;

	
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
	
	public void clockOnContinueButton() {
		clickOnElement(continueButton);
	}
	
	public void clickOnBackButtonOnForgetPassword() {
		clickOnElement(backButtonOnForgePassword);	
		
	}
	
    public boolean isRedirectedToLoginPage() {
        return isElementVisible(loginWithPassword);
    }
    
    public boolean isOtpInstructionVisible() {
        return isElementVisible(otpInstructionText);
    }

    public boolean isEnterOtpHeadingVisible() {
        return isElementVisible(enterOtpHeading);
    }

    public boolean isResendOtpCountdownVisible() {
        return isElementVisible(resendOtpCountdownText);
    }

    public boolean isResendOtpButtonVisible() {
        return isElementVisible(resendOtpButton);
    }

    public String getMaskedPhoneText() {
        return getText(partiallyMaskedPhone);
    }
    
    public boolean isMaskedPhoneVisible() {
        return isElementVisible(maskedPhone);
    }


    public int getOtpResendWaitTimeInSeconds() {
    	String timerText = getText(otpCountdownTimer);
        String[] parts = timerText.split(":");
        int minutes = Integer.parseInt(parts[0]);
        int seconds = Integer.parseInt(parts[1]);
        return minutes * 60 + seconds;
    }   

    @FindBy(xpath = "//div[contains(text(),'attempt') or contains(text(),'Attempt')]")
    private WebElement resendAttemptsText;

    @FindBy(xpath = "//div[contains(text(),'You can resend the OTP in')]/span")
    private WebElement otpCountdownTimer;
    
    @FindBy(xpath = "//button[contains(text(),'Go back to landing page')]")
    private WebElement landingPageLink;
   
    @FindBy(xpath = "//button[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'go back')]")
    private WebElement btnGoBack;

    public static final By BTN_GO_BACK_LOCATOR = By.xpath("//button[contains(text(),'Go back to landing page')]");



    public boolean isResendOtpButtonEnabled() {
        return isButtonEnabled(resendOtpButton);
    }

    public void clickOnResendOtp() {
        clickOnElement(resendOtpButton);
    }

    public String getOtpResendAttemptsText() {
         return getText(resendAttemptsText);        
    }

//	public void clickOnLandingPageLink() {
//		safeClick(btnGoBack);
//	}
	
	public void clickOnLandingPageLink() throws Exception{
		Thread.sleep(2000); // NOT for production, just for debugging
		System.out.println("Current Page Title: " + driver.getCurrentUrl());
		System.out.println("Current Page Title: " + driver.getTitle());
		System.out.println("Button Displayed: " + btnGoBack.isDisplayed());
		System.out.println("Button Enabled: " + btnGoBack.isEnabled());
		System.out.println("Button Text: " + btnGoBack.getText());
		System.out.println("Page contains element ID: " + driver.getPageSource().contains("landing-page-button"));


		safeClickWithRetry(btnGoBack,BTN_GO_BACK_LOCATOR);  // or whatever element you're trying to click
	}
 

}