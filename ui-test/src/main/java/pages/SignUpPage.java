package pages;

import java.util.List;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import base.BasePage;
import utils.OtpReader;

public class SignUpPage extends BasePage {

    public SignUpPage(WebDriver driver) {
        super(driver);
        PageFactory.initElements(driver, this);
    }

    @FindBy(xpath = "//a[@id='signup-url-button']")
    private WebElement signUpLink;

    @FindBy(xpath = "//input[@type='tel']")
    private WebElement telPhoneInput;

    @FindBy(xpath = "//button[@name='continue-button']")
    private WebElement continueButton;

    @FindBy(xpath = "//div[contains(@class, 'pincode-input-container')]//input[contains(@class, 'pincode-input-text')]")
    private List<WebElement> otpInputs;

    @FindBy(id = "verify-otp-button")
    private WebElement verifyOtpButton;

    private String lastEnteredPhone;
    private String cachedOtp;

    public void clickOnSignUp() {
        clickOnElement(signUpLink);
    }

    public void enterPhone() {
        lastEnteredPhone = "630275688";
        enterText(telPhoneInput, lastEnteredPhone);
    }

    public void clickOnContinueAndStoreOtp() throws Exception {
        Thread otpThread = new Thread(() -> {
            try {
                cachedOtp = OtpReader.readOtpFromWebSocket(lastEnteredPhone);
            } catch (Exception ignored) {
            }
        });
        otpThread.start();
        clickOnElement(continueButton);
        otpThread.join(35000);
        if (cachedOtp == null) {
            throw new RuntimeException("OTP was not received");
        }
    }

    public void enterStoredOtp() {
        if (cachedOtp == null) {
            throw new RuntimeException("No OTP available to enter");
        }
        enterOtp(otpInputs, cachedOtp);
    }

    public void clickOnVerifyOtp() {
        clickOnElement(verifyOtpButton);
    }
}
