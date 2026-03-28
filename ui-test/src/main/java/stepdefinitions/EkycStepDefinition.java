package stepdefinitions;

import org.testng.Assert;

import org.openqa.selenium.WebDriver;
import org.apache.log4j.Logger;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.EkycPage;

public class EkycStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(EkycStepDefinition.class);
	EkycPage ekycPage;

	public EkycStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		ekycPage = new EkycPage(driver);
	}

	@Then("verify user navigate to eKYC process steps screen")
	public void verifyUserNavigateToEKycProcessStepsScreen() {
		Assert.assertTrue(ekycPage.isEkycProcessStepsScreenLabelDisplayed(),
				"User didn't navigated to eKYC Process Steps screen");
	}

	@Then("user verify the title of step 1 is choose an eKYC provider")
	public void userVerifyTitleOfStep1IsChooseEkycProvider() {
		Assert.assertTrue(ekycPage.isEkycStep1TitleChooseEkycProviderDisplayed(),
				"Title of the ekyc step 1 not displayed");
	}

	@Then("user verify that the subtitle of step 1 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep1() {
		Assert.assertTrue(ekycPage.isEkycStep1SubtitleDisplayed(), "Subtitle of the ekyc step 1 not displayed");
	}

	@Then("user verify the title of step 2 is terms and conditions")
	public void userVerifyTitleOfStep2IsTermsAndConditions() {
		Assert.assertTrue(ekycPage.isEkycStep2TitleTermsAndConditionsDisplayed(),
				"Title of the ekyc step 2 not displayed");
	}

	@Then("user verify that the subtitle of step 2 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep2() {
		Assert.assertTrue(ekycPage.isEkycStep2SubtitleDisplayed(), "Subtitle of the ekyc step 2 not displayed");
	}

	@Then("user verify the title of step 3 is pre verification guide")
	public void userVerifyTitleOfStep3IsPreVerificationGuide() {
		Assert.assertTrue(ekycPage.isEkycStep3TitlePreVerificationGuideDisplayed(),
				"Title of the ekyc step 3 not displayed");
	}

	@Then("user verify that the subtitle of step 3 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep3() {
		Assert.assertTrue(ekycPage.isEkycStep3SubtitleDisplayed(), "Subtitle of the ekyc step 3 not displayed");
	}

	@Then("user verify the title of step 4 is identity verification")
	public void userVerifyTitleOfStep4IsIdentityVerification() {
		Assert.assertTrue(ekycPage.isEkycStep4TitleIdentityVerificationDisplayed(),
				"Title of the ekyc step 4 not displayed");
	}

	@Then("user verify that the subtitle of step 4 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep4() {
		Assert.assertTrue(ekycPage.isEkycStep4SubtitleDisplayed(), "Subtitle of the ekyc step 4 not displayed");
	}

	@Then("user verify the title of step 5 is review consent")
	public void userVerifyTheTitleOfStep5IsReviewConsent() {
		Assert.assertTrue(ekycPage.isEkycStep5TitleReviewConsentDisplayed(), "Title of the ekyc step 5 not displayed");
	}

	@Then("user verify that the subtitle of step 5 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep5() {
		Assert.assertTrue(ekycPage.isEkycStep5SubtitleDisplayed(), "Subtitle of the ekyc step 5 not displayed");
	}

	@Then("user verify the cancel button is visible in eKYC process steps screen")
	public void userVerifyCancelButtonVisibleInEkycProcessStepsscreen() {
		Assert.assertTrue(ekycPage.isCancelButtonVisible(),
				"cancel button is not visible in eKYC Process Steps screen");
	}

	@Then("user verify the cancel button is clickable in eKYC process steps screen")
	public void userClicksOnCancelButtonInEkycProcessStepsScreen() {
		ekycPage.clickOnCancelButton();
	}

	@Then("user verify warning popup is displayed on clicking cancel button")
	public void userVerifyWarningPopupDisplayed() {
		Assert.assertTrue(ekycPage.isCancelWarningPopupDisplayed(),
				"Warning popup is not displayed after clicking Cancel button");
	}

	@Then("user verify the header is attention in warning popup")
	public void userVerifyWarningPopupHeaderDisplayed() {
		Assert.assertTrue(ekycPage.isWarningPopupHeaderDisplayed(), "Warning popup header is not displayed");
	}

	@Then("user verify the message is displayed in warning popup")
	public void userVerifyWarningPopupMessageDisplayed() {
		Assert.assertTrue(ekycPage.isWarningPopupMessageDisplayed(), "Warning popup message is not displayed");
	}

	@Then("user verify the stay button is visible in warning popup")
	public void userVerifyStayButtonVisible() {
		Assert.assertTrue(ekycPage.isStayButtonVisible(), "Stay button is not visible in warning popup");
	}

	@Then("user verify the discontinue button is visible in warning popup")
	public void userVerifyDiscontinueButtonVisible() {
		Assert.assertTrue(ekycPage.isDiscontinueButtonVisible(), "Discountinue button is not visible in warning popup");
	}

	@When("user verify the stay button is clickable in warning popup")
	public void userClicksOnStayButtonInWarningPopup() {
		ekycPage.clickOnStayButton();
	}

	@Then("user verify warning popup disappeared")
	public void userVerifyWarningPopupDisappeared() {
		Assert.assertFalse(ekycPage.isCancelWarningPopupDisplayed(),
				"Warning popup is not disappeared after clicking stay button");
	}

	@Then("verify user is redirected back to ekycScreen")
	public void verifyUserIsRedirectedBackToEkycScreen() {
		Assert.assertTrue(ekycPage.isEkyScreenVisible(), "User is not redirected back to eKYC Process Steps screen");
	}

	@Then("user click on cancel button in eKYC process steps screen")
	public void userClicksOnCancelButtonInEkycProcessStepsScreenAgain() {
		ekycPage.clickOnCancelButton();
	}

	@When("user verify the discontinue button is clickable in warning popup")
	public void userClicksOnDiscontinueButtonInWarningPopup() {
		ekycPage.clickOnDiscontinueButton();
	}

	@Then("user verify user is redirected to relying party login page")
	public void userVerifyRedirectedToLoginPage() {
		Assert.assertTrue(ekycPage.isLoginPageDisplayed(), "User is not redirected to relying party login page");
	}

	@When("user clicks on sign in with esignet button")
	public void userClicksOnSignInWithEsignetButton() {
		ekycPage.clickOnSignInWithEsignetButton();
	}

	@Then("user verify the proceed button is visible in eKYC process Steps screen")
	public void userVerifyProceedButtonIsVisibleInEKycProcessStepsScreen() {
		Assert.assertTrue(ekycPage.isProceedButtonVisible(),
				"Proceed button is not visible in eKYC process steps screen");
	}

	@When("user verify the proceed button is clickable in eKYC process steps screen")
	public void userClicksOnProceedButtonInEKycProcessStepsScreen() {
		ekycPage.clickOnProceedButton();
	}

	@Then("user verify user is redirected to list of eKYC providers screen")
	public void userVerifyRedirectedToEkycServicesProvidersScreen() {
		Assert.assertTrue(ekycPage.isEkycServiceProviderScreenVisible(),
				"User is not redirected to list of eKYC service providers screen after clicking proceed");
	}

	@Then("user verify the header title in list of eKYC providers screen")
	public void userVerifyHeaderTitleInEkycProvidersScreen() {
		Assert.assertTrue(ekycPage.isEkycProviderHeaderTitleDisplayed(),
				"Header title mismatch in eKYC providers screen");
	}

	@Then("user verify the specific eKYC provider names are visible in list of eKYC providers screen")
	public void userVerifyEkycProviderName() {
		Assert.assertTrue(ekycPage.isEkycSpecificProviderNameDisplayed(), "eKYC provider names are not displayed");
	}

	@Then("user verify foundational ID one and ID two are displayed in list of eKYC providers screen")
	public void userVerifyFoundationalIdsDisplayed() {
		Assert.assertTrue(ekycPage.isEkycProviderFoundationalIdsDisplayed(),
				"Foundational IDs are not displayed in eKYC service provider screen");
	}

	@Then("user verify proceed button is disabled when no eKYC provider is selected in list of eKYC providers screen")
	public void userVerifyProceedButtonIsDisabled() {
		Assert.assertFalse(ekycPage.isProceedButtonEnabled(),
				"Proceed button is enabled without selecting an eKYC provider in eKYC service provider screen");
	}

	@Then("user verify disabled proceed button is not clickable in list of eKYC providers screen")
	public void userVerifyProceedButtonNotClickable() {
		Assert.assertTrue(ekycPage.isProceedButtonNotClickable(),
				"Proceed button is not clickable without selecting an eKYC provider in eKYC service provider screen");
	}

	@Then("user verify the cancel button is visible in the list of eKYC providers screen")
	public void userVerifyCancelButtonVisibleInListOfEkycProviders() {
		Assert.assertTrue(ekycPage.isCancelButtonInEkycProviderScreenVisible(),
				"Cancel button is not visible in eKYC providers screen");
	}

	@Then("user verify the cancel button is clickable in the list of eKYC providers screen")
	public void userClicksOnCancelButtonInListOfEkycProvidersScreen() {
		ekycPage.clickOnCancelButtonInEkycProviderScreen();
	}

	@Then("user verify warning popup is displayed on clicking cancel button in list of eKYC providers screen")
	public void userVerifyCancelWarningPopupDisplayed() {
		Assert.assertTrue(ekycPage.isEycProviderCancelWarningPopupDisplayed(),
				"Warning popup is not displayed after clicking Cancel button");
	}

	@Then("user verify the header in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersWarningPopupHeaderDisplayed() {
		Assert.assertTrue(ekycPage.isEycProviderWarningPopupHeaderDisplayed(), "Warning popup header is not displayed");
	}

	@Then("user verify the message displayed in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersWarningPopupMessageDisplayed() {
		Assert.assertTrue(ekycPage.isEycProviderWarningPopupMessageDisplayed(),
				"Warning popup message is not displayed");
	}

	@Then("user verify the stay button is visible in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersStayButtonVisible() {
		Assert.assertTrue(ekycPage.isStayButtonVisible(), "Stay button is not visible in warning popup");
	}

	@Then("user verify the discontinue button is visible in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersDiscontinueButtonVisible() {
		Assert.assertTrue(ekycPage.isDiscontinueButtonVisible(), "Discountinue button is not visible in warning popup");
	}

	@When("user verify the stay button is clickable in warning popup in list of eKYC providers screen")
	public void userClicksOnStayButtonInEkycProvidersWarningPopup() {
		ekycPage.clickOnStayButton();
	}

	@When("user verify the discontinue button is clickable in warning popup in list of eKYC providers screen")
	public void userClicksOnDiscontinueButtonInEkycProvidersWarningPopup() {
		ekycPage.clickOnDiscontinueButton();
	}

	@Then("user verify the specific eKYC provider names is clickable in list of eKYC providers screen")
	public void userClicksOnSpecificProviderNameButtonInEkycProviderScreen() {
		ekycPage.clickOnSpecificProviderNameButton();
	}

	@Then("user verify the proceed button is clickable in list of eKYC providers screen")
	public void userClicksOnProceedButtonInListOfEkycProvidersScreen() {
		ekycPage.clickOnProceedButtonInEkycProviderScreen();
	}

	@Then("user verify user is redirected to terms and conditions screen")
	public void userVerifyRedirectedToEkycTermsAndConditionScreen() {
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionsScreenVisible(),
				"User is not redirected to terms and coditions screen after clicking Proceed");
	}

}