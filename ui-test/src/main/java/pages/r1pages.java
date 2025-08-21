package pages;

import base.BasePage;
import utils.EsignetConfigManager;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.time.Duration;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;

public class r1pages extends BasePage {

	public r1pages(WebDriver driver) {
		super(driver);
		PageFactory.initElements(driver, this);
	}

	@FindBy(xpath = "//div[@class='grow px-3 text-center font-semibold tracking-normal xs:px-2']")
	WebElement redirectToRegistration;

	@FindBy(xpath = "//div[@class='grow px-3 text-center font-semibold tracking-normal xs:px-2']")
	WebElement headerInRegistrationScreen;

	@FindBy(id = "phone_input")
	WebElement enterMobileNumberTextField;

	@FindBy(id = "continue-button")
	WebElement continueButtonDisplayed;

	@FindBy(id = "continue-button")
	WebElement placeholderOutside;

	@FindBy(id = "back-button")
	WebElement backButtonDisplayed;

	@FindBy(id = "language-select-button")
	WebElement languageSelectionOption;

	@FindBy(xpath = "//*[@id='root']/div/div/div/footer/span")
	WebElement footerTextLogo;

	@FindBy(xpath = "//img[@class='footer-brand-logo']")
	WebElement footerLogoTag;

	@FindBy(xpath = "//div[@id=':r4:-form-item']/span")
	WebElement prefilledCountryCodeDisplayed;

	@FindBy(id = "phone_input")
	WebElement helpTextInTextField;

	@FindBy(id = ":r4:-form-item-message")
	List<WebElement> enterUserNameError;

	@FindBy(id = ":r4:-form-item-message")
	WebElement numberCannotStartWithZeroErrorMassage;

	@FindBy(id = "back-button")
	WebElement backButtonInReg;

	@FindBy(id = "login-header")
	WebElement loginPageHeaders;

	@FindBy(xpath = "//div[@class='w-full text-center text-[22px] font-semibold']")
	WebElement otpPageDisplayed;

	@FindBy(xpath = "//div[@class='text-muted-neutral-gray']")
	WebElement otpPageDescriptionDisplay;

	@FindBy(xpath = "//div[@class='pincode-input-container']/input")
	List<WebElement> otpInputFieldsVisible;
	
	@FindBy(xpath = "//div[@class='pincode-input-container']")
	WebElement otpInputFields;
	
	@FindBy(id = "verify-otp-button")
	WebElement verifyOtpButtonDisplay;

	@FindBy(xpath = "//div[@class='flex gap-x-1 text-center']/span")
	WebElement otpCountDownTimerDisplayed;

	@FindBy(id = "resend-otp-button")
	WebElement resendOtpButtonDisplay;
	
	@FindBy(xpath = "//p[@class='truncate text-xs text-destructive']")
	WebElement otpExpiredErrorDisplay;
	
	@FindBy(id = "cross_icon")
	WebElement errorCloseIconDisplay;
	
	@FindBy(xpath = "//p[@class='w-max rounded-md bg-[#FFF7E5] p-2 px-8 text-center text-sm font-semibold text-[#8B6105]']")
	WebElement attemptLeftOptionDisplay;

	public boolean isRedirectedToMobileNumberRegistrationPage() {
		return redirectToRegistration.isDisplayed();
	}

	public boolean isHeaderInRegistrationScreenDisplayed() {
		return headerInRegistrationScreen.isDisplayed();
	}

	public boolean isEnterMobileNumberTextFieldDisplayed() {
		return enterMobileNumberTextField.isDisplayed();
	}

	public boolean isContinueButtonDisplayed() {
		return continueButtonDisplayed.isDisplayed();
	}

	public boolean isBackButtonDisplayed() {
		return backButtonDisplayed.isDisplayed();
	}

	public boolean isLanguageSelectionOptionDisplayed() {
		return languageSelectionOption.isDisplayed();
	}

	public boolean isFooterTextDisplayed() {
		return footerTextLogo.isDisplayed();
	}

	public boolean isFooterLogoDisplayed() {
		return footerLogoTag.isDisplayed();
	}

	public boolean isPrefilledCountryCodeDisplayed() {
		return prefilledCountryCodeDisplayed.isDisplayed();
	}

	private String lastEnteredMobileNumber;

	public void enterMobileNumber(String number) {
		enterMobileNumberTextField.clear();
		enterText(enterMobileNumberTextField, number);
		enterMobileNumberTextField.clear();
		lastEnteredMobileNumber = "+855 " + number;
	}

	public void userTabsOut() {
	    enterMobileNumberTextField.sendKeys(Keys.TAB);
	}

	public void enterLessThanEightDigitMobileNumber(String number) {
		enterMobileNumberTextField.clear();
		enterMobileNumberTextField.sendKeys(number);
	}

	public boolean isErrorMessageDisplayed() {
		return !enterUserNameError.isEmpty() && enterUserNameError.get(0).isDisplayed();
	}

	public boolean isNumberCannotStartWithZeroErrorDisplayed() {
		return numberCannotStartWithZeroErrorMassage.isDisplayed();
	}

	public boolean isEnterValidUsernameErrorDisplayed() {
		return !enterUserNameError.isEmpty() && enterUserNameError.get(0).isDisplayed();
	}

	public boolean isContinueButtonDisabled() {
		return !continueButtonDisplayed.isEnabled();
	}

	public boolean isContinueButtonEnabled() {
		return isButtonEnabled(continueButtonDisplayed);
	}
	
	public void clickOnBackButton() {
	    backButtonDisplayed.click();
	}
	
	public boolean isPreviousScreenVisible() {
	    return isElementVisible(loginPageHeaders);
	}
	
	public void clickContinueButton() {
	    continueButtonDisplayed.click();
	}
	
	public boolean isOtpPageVisible() {
	    return otpPageDisplayed.isDisplayed();
	}
	
	public boolean isEnterOtpPageDisplayed() {
		return isElementVisible(otpPageDescriptionDisplay);
	}
	
	public boolean isOtpInputFieldVisible() {
	    return otpInputFields.isDisplayed();
	}
	
	public boolean isVerifyOtpButtonVisible() {
	    return verifyOtpButtonDisplay.isDisplayed();
	}
	
	public boolean isOtpCountDownTimerVisible() {
	    return otpCountDownTimerDisplayed.isDisplayed();
	}
	
	public boolean isResendOtpButtonVisible() {
	    return resendOtpButtonDisplay.isDisplayed();
	}
	
	public boolean isBackToEditMobileNumberOptionVisible() {
	    return isElementVisible(backButtonInReg);
	}
	
	public void clickBackButtonOnOtpScreen() {
	    backButtonInReg.click();
	}
	
	public void waitUntilOtpExpires() {
	    int otpExpiry = Integer.parseInt(EsignetConfigManager.getProperty("otp.expiry.seconds", ""));
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(otpExpiry));
	    wait.until(ExpectedConditions.textToBePresentInElement(otpCountDownTimerDisplayed, "00:00"));
	}
	
	public void enterOtp(String otp) {
		for (WebElement field : otpInputFieldsVisible) {
			field.click();
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
			field.sendKeys(Keys.BACK_SPACE);
		}
		for (int i = 0; i < otp.length(); i++) {
			WebElement field = otpInputFieldsVisible.get(i);
			field.click();
			field.sendKeys(String.valueOf(otp.charAt(i)));
		}
	}
	
	public void clickOnVerifyOtpButton() {
        verifyOtpButtonDisplay.click();
    }
	
	public boolean isOtpExpiredMessageDisplayed() {
		return isElementVisible(otpExpiredErrorDisplay);
	}
	
	public void clickOnErrorCloseIcon() {
		clickOnElement(errorCloseIconDisplay);
	}
	
	public void verifyErrorMessageIsNotDisplayed() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
        wait.until(ExpectedConditions.invisibilityOf(otpExpiredErrorDisplay));
    }
	
	public void clickOnResendOtpButton() {
		clickOnElement(resendOtpButtonDisplay);
	}
	
	public void checkAttemptLeftInOtpScreen() {
	    attemptLeftOptionDisplay.isDisplayed();
	}
	
	public void userIsOnOtpScreen() {
        otpPageDisplayed.isDisplayed();
    }
	
	
	
	
	
	

	
	
	
	
	
	



}
