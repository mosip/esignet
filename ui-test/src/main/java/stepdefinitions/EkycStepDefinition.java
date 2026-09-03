package stepdefinitions;

import org.testng.Assert;

import org.openqa.selenium.WebDriver;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.EkycPage;
import pages.LoginOptionsPage;

public class EkycStepDefinition {

	public WebDriver driver;
	EkycPage ekycPage;
	LoginOptionsPage loginOptionsPage;

	public EkycStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		ekycPage = new EkycPage(driver);
		loginOptionsPage = new LoginOptionsPage(driver);
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
		Assert.assertTrue(ekycPage.isEkycProcessStepsScreenLabelDisplayed(),
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
		if (ekycPage.isAlreadyOnRelyingParty()) {
			Assert.assertTrue(ekycPage.isLoginPageDisplayed(), "User is not redirected to relying party login page");
			return;
		}
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
				"Proceed button is clickable without selecting an eKYC provider in eKYC service provider screen");
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
		Assert.assertTrue(ekycPage.isEkycProviderCancelWarningPopupDisplayed(),
				"Warning popup is not displayed after clicking Cancel button");
	}

	@Then("user verify the header in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersWarningPopupHeaderDisplayed() {
		Assert.assertTrue(ekycPage.isEkycProviderWarningPopupHeaderDisplayed(),
				"Warning popup header is not displayed");
	}

	@Then("user verify the message displayed in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersWarningPopupMessageDisplayed() {
		Assert.assertTrue(ekycPage.isEkycProviderWarningPopupMessageDisplayed(),
				"Warning popup message is not displayed");
	}

	@Then("user verify the stay button is visible in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersStayButtonVisible() {
		Assert.assertTrue(ekycPage.isStayButtonVisible(), "Stay button is not visible in warning popup");
	}

	@Then("user verify the discontinue button is visible in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersDiscontinueButtonVisible() {
		Assert.assertTrue(ekycPage.isDiscontinueButtonVisible(), "Discontinue button is not visible in warning popup");
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
	public void userClicksOnSpecificProviderNameInEkycProviderScreen() {
		ekycPage.clickOnSpecificProviderName();
	}

	@Then("user verify the proceed button is clickable in list of eKYC providers screen")
	public void userClicksOnProceedButtonInListOfEkycProvidersScreen() {
		ekycPage.clickOnProceedButtonInEkycProviderScreen();
	}

	@Then("user verify user is redirected to terms and conditions screen")
	public void userVerifyRedirectedToEkycTermsAndConditionScreen() {
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionsScreenVisible(),
				"User is not redirected to terms and conditions screen after clicking Proceed");
	}

	@Then("user verify the header displayed in terms and conditions screen")
	public void userVerifyTermsAndConditionHeaderDisplayed() {
		Assert.assertTrue(ekycPage.isTermsAndConditionHeaderDisplayed(), "Header is not displayed");
	}

	@Then("user verify the sub header message displayed in terms and conditions screen")
	public void userVerifyTermsAndConditionSubHeaderDisplayed() {
		Assert.assertTrue(ekycPage.isTermsAndConditionSubHeaderDisplayed(), "Sub header message is not displayed");
	}

	@Then("user verify the content displayed in terms and conditions screen")
	public void userVerifyTermsAndConditionContentDisplayed() {
		Assert.assertTrue(ekycPage.isTermsAndConditionContentDisplayed(), "Content message is not displayed");
	}

	@Then("user verify content body text frame has scrollbar enabled in terms and conditions screen")
	public void userVerifyTermsAndConditionContentScrollBarIsEnabled() {
		Assert.assertTrue(ekycPage.isTermsAndConditionContentScrollBarVisible(),
				"Terms and conditions content body text frame scroll bar is visible");
	}

	@Then("user verify checkbox is not selected by default in terms and conditions screen")
	public void userVerifyCheckboxNotSelectedByDefault() {
		Assert.assertTrue(ekycPage.isTermsAndConditionCheckboxNotSelected(),
				"Terms and Conditions checkbox is selected by default");
	}

	@Then("user click on checkbox in terms and conditions screen")
	public void userClicksOnCheckBox() {
		ekycPage.clickOnTermsAndConditionCheckBox();
	}

	@Then("user verify the text beside checkbox message displayed in terms and conditions screen")
	public void userVerifyTermsCheckboxTextDisplayed() {
		Assert.assertTrue(ekycPage.isTermsCheckboxTextDisplayed(),
				"Text beside checkbox is not displayed in terms and conditions screen");
	}

	@Then("user verify the cancel button is visible in terms and conditions screen")
	public void userVerifyCancelButtonVisibleInTermsAndCondition() {
		Assert.assertTrue(ekycPage.isCancelButtonInTermsAndConditionScreenVisible(),
				"Cancel button is not visible in terms and conditions screen");
	}

	@Then("user verify the cancel button is clickable in terms and conditions screen")
	public void userClicksOnCancelButtonInTermsAndConditionScreen() {
		ekycPage.clickOnCancelButtonInTermsAndConditionScreen();
	}

	@Then("user verify warning popup is displayed on clicking cancel button in terms and conditions screen")
	public void userVerifyTermsWarningPopupDisplayed() {
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionWarningPopupDisplayed(),
				"Warning popup is not displayed after clicking Cancel button");
	}

	@When("user verify the stay button is clickable in warning popup in terms and conditions screen")
	public void userClicksOnStayButtonInEkyTermsAndConditionWarningPopup() {
		ekycPage.clickOnStayButton();
	}

	@When("user verify the discontinue button is clickable in warning popup in terms and conditions screen")
	public void userClicksOnDiscontinueButtonInTermsWarningPopup() {
		ekycPage.clickOnDiscontinueButton();
	}

	@Then("user verify proceed button is disabled when no check box is selected in terms and condition screen")
	public void userVerifyTermsProceedButtonIsDisabled() {
		Assert.assertFalse(ekycPage.isTermsProceedButtonEnabled(),
				"Proceed button is enabled without selecting an checkbox in terms and condition screen");
	}

	@Then("user verify the proceed button is displayed in terms and condition screen")
	public void userVerifyTermsProceedButtonDisplayed() {
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionProceedButtonDisplayed(),
				"Proceed button is not visible in terms and condition screen");
	}

	@Then("user verify the proceed button is enabled after selecting check box in terms and conditions screen")
	public void userVerifyProceedButtonIsEnabled() {
		Assert.assertTrue(ekycPage.isTermsProceedButtonEnabled(),
				"Proceed button is not enabled after selecting the checkbox in terms and condition screen");
	}

	@Then("verify user is redirected back to terms and conditions screen")
	public void verifyUserIsRedirectedBackToTermsAndConditionScreen() {
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionScreenVisible(),
				"User is not redirected back to eKYC terms and condition screen");
	}

	@When("user navigates back in the browser from eKYC process steps screen and a leave site prompt should appear")
	public void userNavigatesBackFromEkycProcessStepsScreenAndLeaveSitePromptShouldAppear() {
		driver.navigate().back();
		Assert.assertTrue(ekycPage.isLeaveSitePromptDisplayed(10),
				"The 'Leave site?' prompt did not appear after navigating back from the eKYC process steps screen");
	}

	@When("user cancels the leave site prompt in eKYC process steps screen")
	public void userCancelsLeaveSitePromptInEkycProcessStepsScreen() {
		ekycPage.dismissAlert();
	}

	@Then("verify user is retained on eKYC process steps screen")
	public void verifyUserIsRetainedOnEkycProcessStepsScreen() {
		Assert.assertTrue(ekycPage.isEkycProcessStepsScreenLabelDisplayed(),
				"User is not retained on eKYC process steps screen");
	}

	@When("user refreshes the browser from eKYC process steps screen and a leave site prompt should appear")
	public void userRefreshesBrowserFromEkycProcessStepsScreenAndLeaveSitePromptShouldAppear() {
		driver.navigate().refresh();
		Assert.assertTrue(ekycPage.isLeaveSitePromptDisplayed(10),
				"The 'Leave site?' prompt did not appear after refreshing the browser from the eKYC process steps screen");
	}

	@When("user confirms the leave site prompt in eKYC process steps screen")
	public void userConfirmsLeaveSitePromptInEkycProcessStepsScreen() {
		ekycPage.acceptAlert();
	}

	@Then("verify user is no longer on eKYC process steps screen")
	public void verifyUserIsNoLongerOnEkycProcessStepsScreen() {
		Assert.assertFalse(ekycPage.isEkyScreenVisible(), "User is still on the eKYC process steps screen");
	}

	@Then("verify user is redirected to the relying party with the consent not shared error")
	public void verifyUserIsRedirectedToRelyingPartyWithConsentNotSharedError() {
		Assert.assertTrue(loginOptionsPage.waitForRedirectToRelyingPartyWithError("consent_not_shared"),
				"User was not redirected to the relying party with the consent_not_shared error");
	}

	@Then("verify the authorization failed popup is displayed")
	public void verifyAuthorizationFailedPopupIsDisplayed() {
		Assert.assertTrue(ekycPage.isAuthorizationFailedPopupDisplayed(),
				"The authorization failed popup is not displayed");
	}

	@When("user clicks Okay on the authorization failed popup")
	public void userClicksOkayOnAuthorizationFailedPopup() {
		ekycPage.clickOkayOnAuthorizationFailedPopup();
	}

}
