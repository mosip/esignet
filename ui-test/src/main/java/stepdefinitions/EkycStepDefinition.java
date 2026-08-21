package stepdefinitions;

import org.testng.Assert;

import org.openqa.selenium.WebDriver;
import org.apache.log4j.Logger;

import base.BaseTest;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.EkycPage;
import utils.EsignetUtil;

public class EkycStepDefinition {

	public WebDriver driver;
	private static final Logger logger = Logger.getLogger(EkycStepDefinition.class);
	EkycPage ekycPage;

	public EkycStepDefinition(BaseTest baseTest) {
		this.driver = baseTest.getDriver();
		ekycPage = new EkycPage(driver);
	}

	private boolean notApplicableUnderMockPlugin(String featureDescription) {
		return EsignetUtil.notApplicableUnderMockPlugin(featureDescription, logger);
	}

	@Then("verify user navigate to eKYC process steps screen")
	public void verifyUserNavigateToEKycProcessStepsScreen() {
		if (notApplicableUnderMockPlugin("the eKYC process-steps screen")) {
			return;
		}
		if (ekycPage.waitForRelyingPartyRedirectOrElement(org.openqa.selenium.By.id("tnc-header"), 60)) {
			String reason = "login completed directly to the relying party with no eKYC screen.";
			logger.info("Not checking - " + reason);
			utils.ExtentReportManager.notApplicable(reason);
			return;
		}
		Assert.assertTrue(ekycPage.isEkycProcessStepsScreenLabelDisplayed(),
				"User didn't navigated to eKYC Process Steps screen");
	}

	@Then("user verify the title of step 1 is choose an eKYC provider")
	public void userVerifyTitleOfStep1IsChooseEkycProvider() {
		if (notApplicableUnderMockPlugin("eKYC step 1 title")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep1TitleChooseEkycProviderDisplayed(),
				"Title of the ekyc step 1 not displayed");
	}

	@Then("user verify that the subtitle of step 1 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep1() {
		if (notApplicableUnderMockPlugin("eKYC step 1 subtitle")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep1SubtitleDisplayed(), "Subtitle of the ekyc step 1 not displayed");
	}

	@Then("user verify the title of step 2 is terms and conditions")
	public void userVerifyTitleOfStep2IsTermsAndConditions() {
		if (notApplicableUnderMockPlugin("eKYC step 2 title")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep2TitleTermsAndConditionsDisplayed(),
				"Title of the ekyc step 2 not displayed");
	}

	@Then("user verify that the subtitle of step 2 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep2() {
		if (notApplicableUnderMockPlugin("eKYC step 2 subtitle")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep2SubtitleDisplayed(), "Subtitle of the ekyc step 2 not displayed");
	}

	@Then("user verify the title of step 3 is pre verification guide")
	public void userVerifyTitleOfStep3IsPreVerificationGuide() {
		if (notApplicableUnderMockPlugin("eKYC step 3 title")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep3TitlePreVerificationGuideDisplayed(),
				"Title of the ekyc step 3 not displayed");
	}

	@Then("user verify that the subtitle of step 3 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep3() {
		if (notApplicableUnderMockPlugin("eKYC step 3 subtitle")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep3SubtitleDisplayed(), "Subtitle of the ekyc step 3 not displayed");
	}

	@Then("user verify the title of step 4 is identity verification")
	public void userVerifyTitleOfStep4IsIdentityVerification() {
		if (notApplicableUnderMockPlugin("eKYC step 4 title")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep4TitleIdentityVerificationDisplayed(),
				"Title of the ekyc step 4 not displayed");
	}

	@Then("user verify that the subtitle of step 4 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep4() {
		if (notApplicableUnderMockPlugin("eKYC step 4 subtitle")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep4SubtitleDisplayed(), "Subtitle of the ekyc step 4 not displayed");
	}

	@Then("user verify the title of step 5 is review consent")
	public void userVerifyTheTitleOfStep5IsReviewConsent() {
		if (notApplicableUnderMockPlugin("eKYC step 5 title")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep5TitleReviewConsentDisplayed(), "Title of the ekyc step 5 not displayed");
	}

	@Then("user verify that the subtitle of step 5 is displayed in eKYC process steps screen")
	public void userVerifyTheSubtitleOfStep5() {
		if (notApplicableUnderMockPlugin("eKYC step 5 subtitle")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycStep5SubtitleDisplayed(), "Subtitle of the ekyc step 5 not displayed");
	}

	@Then("user verify the cancel button is visible in eKYC process steps screen")
	public void userVerifyCancelButtonVisibleInEkycProcessStepsscreen() {
		if (notApplicableUnderMockPlugin("eKYC process steps cancel button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isCancelButtonVisible(),
				"cancel button is not visible in eKYC Process Steps screen");
	}

	@Then("user verify the cancel button is clickable in eKYC process steps screen")
	public void userClicksOnCancelButtonInEkycProcessStepsScreen() {
		if (notApplicableUnderMockPlugin("eKYC process steps cancel button")) {
			return;
		}
		ekycPage.clickOnCancelButton();
	}

	@Then("user verify warning popup is displayed on clicking cancel button")
	public void userVerifyWarningPopupDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC cancel warning popup")) {
			return;
		}
		Assert.assertTrue(ekycPage.isCancelWarningPopupDisplayed(),
				"Warning popup is not displayed after clicking Cancel button");
	}

	@Then("user verify the header is attention in warning popup")
	public void userVerifyWarningPopupHeaderDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC warning popup header")) {
			return;
		}
		Assert.assertTrue(ekycPage.isWarningPopupHeaderDisplayed(), "Warning popup header is not displayed");
	}

	@Then("user verify the message is displayed in warning popup")
	public void userVerifyWarningPopupMessageDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC warning popup message")) {
			return;
		}
		Assert.assertTrue(ekycPage.isWarningPopupMessageDisplayed(), "Warning popup message is not displayed");
	}

	@Then("user verify the stay button is visible in warning popup")
	public void userVerifyStayButtonVisible() {
		if (notApplicableUnderMockPlugin("eKYC warning popup stay button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isStayButtonVisible(), "Stay button is not visible in warning popup");
	}

	@Then("user verify the discontinue button is visible in warning popup")
	public void userVerifyDiscontinueButtonVisible() {
		if (notApplicableUnderMockPlugin("eKYC warning popup discontinue button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isDiscontinueButtonVisible(), "Discountinue button is not visible in warning popup");
	}

	@When("user verify the stay button is clickable in warning popup")
	public void userClicksOnStayButtonInWarningPopup() {
		if (notApplicableUnderMockPlugin("eKYC warning popup stay button")) {
			return;
		}
		ekycPage.clickOnStayButton();
	}

	@Then("user verify warning popup disappeared")
	public void userVerifyWarningPopupDisappeared() {
		if (notApplicableUnderMockPlugin("eKYC process steps screen")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycProcessStepsScreenLabelDisplayed(),
				"Warning popup is not disappeared after clicking stay button");
	}

	@Then("verify user is redirected back to ekycScreen")
	public void verifyUserIsRedirectedBackToEkycScreen() {
		if (notApplicableUnderMockPlugin("eKYC process steps screen")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkyScreenVisible(), "User is not redirected back to eKYC Process Steps screen");
	}

	@Then("user click on cancel button in eKYC process steps screen")
	public void userClicksOnCancelButtonInEkycProcessStepsScreenAgain() {
		if (notApplicableUnderMockPlugin("eKYC process steps cancel button")) {
			return;
		}
		ekycPage.clickOnCancelButton();
	}

	@When("user verify the discontinue button is clickable in warning popup")
	public void userClicksOnDiscontinueButtonInWarningPopup() {
		if (notApplicableUnderMockPlugin("eKYC warning popup discontinue button")) {
			return;
		}
		ekycPage.clickOnDiscontinueButton();
	}

	@Then("user verify user is redirected to relying party login page")
	public void userVerifyRedirectedToLoginPage() {
		if (notApplicableUnderMockPlugin("relying party's own login page (eKYC discontinue flow)")) {
			return;
		}
		Assert.assertTrue(ekycPage.isLoginPageDisplayed(), "User is not redirected to relying party login page");
	}

	@When("user clicks on sign in with esignet button")
	public void userClicksOnSignInWithEsignetButton() {
		if (notApplicableUnderMockPlugin("relying party's sign-in-with-esignet button")) {
			return;
		}
		ekycPage.clickOnSignInWithEsignetButton();
	}

	@Then("user verify the proceed button is visible in eKYC process Steps screen")
	public void userVerifyProceedButtonIsVisibleInEKycProcessStepsScreen() {
		if (notApplicableUnderMockPlugin("eKYC process steps proceed button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isProceedButtonVisible(),
				"Proceed button is not visible in eKYC process steps screen");
	}

	@When("user verify the proceed button is clickable in eKYC process steps screen")
	public void userClicksOnProceedButtonInEKycProcessStepsScreen() {
		if (notApplicableUnderMockPlugin("eKYC process steps proceed button")) {
			return;
		}
		ekycPage.clickOnProceedButton();
	}

	@Then("user verify user is redirected to list of eKYC providers screen")
	public void userVerifyRedirectedToEkycServicesProvidersScreen() {
		if (notApplicableUnderMockPlugin("eKYC providers list screen")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycServiceProviderScreenVisible(),
				"User is not redirected to list of eKYC service providers screen after clicking proceed");
	}

	@Then("user verify the header title in list of eKYC providers screen")
	public void userVerifyHeaderTitleInEkycProvidersScreen() {
		if (notApplicableUnderMockPlugin("eKYC providers list header")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycProviderHeaderTitleDisplayed(),
				"Header title mismatch in eKYC providers screen");
	}

	@Then("user verify the specific eKYC provider names are visible in list of eKYC providers screen")
	public void userVerifyEkycProviderName() {
		if (notApplicableUnderMockPlugin("eKYC provider names")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycSpecificProviderNameDisplayed(), "eKYC provider names are not displayed");
	}

	@Then("user verify foundational ID one and ID two are displayed in list of eKYC providers screen")
	public void userVerifyFoundationalIdsDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC provider foundational IDs")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycProviderFoundationalIdsDisplayed(),
				"Foundational IDs are not displayed in eKYC service provider screen");
	}

	@Then("user verify proceed button is disabled when no eKYC provider is selected in list of eKYC providers screen")
	public void userVerifyProceedButtonIsDisabled() {
		if (notApplicableUnderMockPlugin("eKYC providers proceed button")) {
			return;
		}
		Assert.assertFalse(ekycPage.isProceedButtonEnabled(),
				"Proceed button is enabled without selecting an eKYC provider in eKYC service provider screen");
	}

	@Then("user verify disabled proceed button is not clickable in list of eKYC providers screen")
	public void userVerifyProceedButtonNotClickable() {
		if (notApplicableUnderMockPlugin("eKYC providers proceed button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isProceedButtonNotClickable(),
				"Proceed button is clickable without selecting an eKYC provider in eKYC service provider screen");
	}

	@Then("user verify the cancel button is visible in the list of eKYC providers screen")
	public void userVerifyCancelButtonVisibleInListOfEkycProviders() {
		if (notApplicableUnderMockPlugin("eKYC providers cancel button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isCancelButtonInEkycProviderScreenVisible(),
				"Cancel button is not visible in eKYC providers screen");
	}

	@Then("user verify the cancel button is clickable in the list of eKYC providers screen")
	public void userClicksOnCancelButtonInListOfEkycProvidersScreen() {
		if (notApplicableUnderMockPlugin("eKYC providers cancel button")) {
			return;
		}
		ekycPage.clickOnCancelButtonInEkycProviderScreen();
	}

	@Then("user verify warning popup is displayed on clicking cancel button in list of eKYC providers screen")
	public void userVerifyCancelWarningPopupDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC providers cancel warning popup")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycProviderCancelWarningPopupDisplayed(),
				"Warning popup is not displayed after clicking Cancel button");
	}

	@Then("user verify the header in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersWarningPopupHeaderDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC providers warning popup header")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycProviderWarningPopupHeaderDisplayed(),
				"Warning popup header is not displayed");
	}

	@Then("user verify the message displayed in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersWarningPopupMessageDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC providers warning popup message")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycProviderWarningPopupMessageDisplayed(),
				"Warning popup message is not displayed");
	}

	@Then("user verify the stay button is visible in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersStayButtonVisible() {
		if (notApplicableUnderMockPlugin("eKYC providers warning popup stay button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isStayButtonVisible(), "Stay button is not visible in warning popup");
	}

	@Then("user verify the discontinue button is visible in warning popup in list of eKYC providers screen")
	public void userVerifyEkycProvidersDiscontinueButtonVisible() {
		if (notApplicableUnderMockPlugin("eKYC providers warning popup discontinue button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isDiscontinueButtonVisible(), "Discontinue button is not visible in warning popup");
	}

	@When("user verify the stay button is clickable in warning popup in list of eKYC providers screen")
	public void userClicksOnStayButtonInEkycProvidersWarningPopup() {
		if (notApplicableUnderMockPlugin("eKYC providers warning popup stay button")) {
			return;
		}
		ekycPage.clickOnStayButton();
	}

	@When("user verify the discontinue button is clickable in warning popup in list of eKYC providers screen")
	public void userClicksOnDiscontinueButtonInEkycProvidersWarningPopup() {
		if (notApplicableUnderMockPlugin("eKYC providers warning popup discontinue button")) {
			return;
		}
		ekycPage.clickOnDiscontinueButton();
	}

	@Then("user verify the specific eKYC provider names is clickable in list of eKYC providers screen")
	public void userClicksOnSpecificProviderNameInEkycProviderScreen() {
		if (notApplicableUnderMockPlugin("eKYC provider names")) {
			return;
		}
		ekycPage.clickOnSpecificProviderName();
	}

	@Then("user verify the proceed button is clickable in list of eKYC providers screen")
	public void userClicksOnProceedButtonInListOfEkycProvidersScreen() {
		if (notApplicableUnderMockPlugin("eKYC providers proceed button")) {
			return;
		}
		ekycPage.clickOnProceedButtonInEkycProviderScreen();
	}

	@Then("user verify user is redirected to terms and conditions screen")
	public void userVerifyRedirectedToEkycTermsAndConditionScreen() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions screen")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionsScreenVisible(),
				"User is not redirected to terms and conditions screen after clicking Proceed");
	}

	@Then("user verify the header displayed in terms and conditions screen")
	public void userVerifyTermsAndConditionHeaderDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions header")) {
			return;
		}
		Assert.assertTrue(ekycPage.isTermsAndConditionHeaderDisplayed(), "Header is not displayed");
	}

	@Then("user verify the sub header message displayed in terms and conditions screen")
	public void userVerifyTermsAndConditionSubHeaderDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions sub header")) {
			return;
		}
		Assert.assertTrue(ekycPage.isTermsAndConditionSubHeaderDisplayed(), "Sub header message is not displayed");
	}

	@Then("user verify the content displayed in terms and conditions screen")
	public void userVerifyTermsAndConditionContentDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions content")) {
			return;
		}
		Assert.assertTrue(ekycPage.isTermsAndConditionContentDisplayed(), "Content message is not displayed");
	}

	@Then("user verify content body text frame has scrollbar enabled in terms and conditions screen")
	public void userVerifyTermsAndConditionContentScrollBarIsEnabled() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions content scrollbar")) {
			return;
		}
		Assert.assertTrue(ekycPage.isTermsAndConditionContentScrollBarVisible(),
				"Terms and conditions content body text frame scroll bar is visible");
	}

	@Then("user verify checkbox is not selected by default in terms and conditions screen")
	public void userVerifyCheckboxNotSelectedByDefault() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions checkbox")) {
			return;
		}
		Assert.assertTrue(ekycPage.isTermsAndConditionCheckboxNotSelected(),
				"Terms and Conditions checkbox is selected by default");
	}

	@Then("user click on checkbox in terms and conditions screen")
	public void userClicksOnCheckBox() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions checkbox")) {
			return;
		}
		ekycPage.clickOnTermsAndConditionCheckBox();
	}

	@Then("user verify the text beside checkbox message displayed in terms and conditions screen")
	public void userVerifyTermsCheckboxTextDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions checkbox text")) {
			return;
		}
		Assert.assertTrue(ekycPage.isTermsCheckboxTextDisplayed(),
				"Text beside checkbox is not displayed in terms and conditions screen");
	}

	@Then("user verify the cancel button is visible in terms and conditions screen")
	public void userVerifyCancelButtonVisibleInTermsAndCondition() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions cancel button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isCancelButtonInTermsAndConditionScreenVisible(),
				"Cancel button is not visible in terms and conditions screen");
	}

	@Then("user verify the cancel button is clickable in terms and conditions screen")
	public void userClicksOnCancelButtonInTermsAndConditionScreen() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions cancel button")) {
			return;
		}
		ekycPage.clickOnCancelButtonInTermsAndConditionScreen();
	}

	@Then("user verify warning popup is displayed on clicking cancel button in terms and conditions screen")
	public void userVerifyTermsWarningPopupDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions warning popup")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionWarningPopupDisplayed(),
				"Warning popup is not displayed after clicking Cancel button");
	}

	@When("user verify the stay button is clickable in warning popup in terms and conditions screen")
	public void userClicksOnStayButtonInEkyTermsAndConditionWarningPopup() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions warning popup stay button")) {
			return;
		}
		ekycPage.clickOnStayButton();
	}

	@When("user verify the discontinue button is clickable in warning popup in terms and conditions screen")
	public void userClicksOnDiscontinueButtonInTermsWarningPopup() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions warning popup discontinue button")) {
			return;
		}
		ekycPage.clickOnDiscontinueButton();
	}

	@Then("user verify proceed button is disabled when no check box is selected in terms and condition screen")
	public void userVerifyTermsProceedButtonIsDisabled() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions proceed button")) {
			return;
		}
		Assert.assertFalse(ekycPage.isTermsProceedButtonEnabled(),
				"Proceed button is enabled without selecting an checkbox in terms and condition screen");
	}

	@Then("user verify the proceed button is displayed in terms and condition screen")
	public void userVerifyTermsProceedButtonDisplayed() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions proceed button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionProceedButtonDisplayed(),
				"Proceed button is not visible in terms and condition screen");
	}

	@Then("user verify the proceed button is enabled after selecting check box in terms and conditions screen")
	public void userVerifyProceedButtonIsEnabled() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions proceed button")) {
			return;
		}
		Assert.assertTrue(ekycPage.isTermsProceedButtonEnabled(),
				"Proceed button is not enabled after selecting the checkbox in terms and condition screen");
	}

	@Then("verify user is redirected back to terms and conditions screen")
	public void verifyUserIsRedirectedBackToTermsAndConditionScreen() {
		if (notApplicableUnderMockPlugin("eKYC terms and conditions screen")) {
			return;
		}
		Assert.assertTrue(ekycPage.isEkycTermsAndConditionScreenVisible(),
				"User is not redirected back to eKYC terms and condition screen");
	}

}
