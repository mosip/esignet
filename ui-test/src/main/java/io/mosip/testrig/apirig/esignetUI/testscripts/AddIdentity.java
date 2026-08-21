package io.mosip.testrig.apirig.esignetUI.testscripts;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.testrunner.JsonPrecondtion;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.GlobalMethods;
import io.mosip.testrig.apirig.utils.KernelAuthentication;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.mosip.testrig.apirig.utils.RestClient;
import io.mosip.testrig.apirig.utils.SecurityXSSException;
import io.restassured.response.Response;
import utils.EsignetConfigManager;
import utils.EsignetUtil;

public class AddIdentity extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(AddIdentity.class);
	protected String testCaseName = "";
	public Response response = null;
	private boolean isWaitRequired = false;

	/**
	 * get current testcaseName
	 */
	@Override
	public String getTestName() {
		return testCaseName;

	}

	@BeforeClass
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	/**
	 * Data provider class provides test case list
	 *
	 * @return object of data provider
	 */
	@DataProvider(name = "testcaselist")
	public Object[] getTestCaseList(ITestContext context) {
		String ymlFile = context.getCurrentXmlTest().getLocalParameters().get("ymlFile");
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	/**
	 * Test method for OTP Generation execution
	 *
	 * @param objTestParameters
	 * @param testScenario
	 * @param testcaseName
	 * @throws Exception
	 */
	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO) throws Exception, SecurityXSSException {
		testCaseName = testCaseDTO.getTestCaseName();
		testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);

		boolean isMockIdentitySystem = testCaseDTO.getEndPoint().contains("mock-identity-system");
		if (isMockIdentitySystem && EsignetUtil.isSunbirdAuthenticatorActive()) {
			throw new SkipException(
					"Skipped: " + testCaseName + " - mock-identity-system identity is not used on a Sunbird RC-backed server");
		}
		// A configured uin/mockUin already skips (with its UIN written) above; uinPhoneNumber alone
		// only overrides the OTP-login phone and must not skip identity creation on its own, or no UIN
		// ever gets written for downstream $ID:...AddIdentity..._UIN$ resolution to consume.
		writeConfigValueAndSkipIfProvided(isMockIdentitySystem ? "mockUin" : "uin", testCaseName, "UIN");

		String mockInputJson = null;

		if (isMockIdentitySystem) {
			String url = ApplnURI.replace("-internal", "") + testCaseDTO.getEndPoint();

			mockInputJson = generateDynamicMockIdentityRequest(getMockIdentitySchema(), testCaseName);

			mockInputJson = EsignetUtil.inputstringKeyWordHandler(mockInputJson, testCaseName);

			GlobalMethods.reportRequest(null, mockInputJson, url);

			response = RestClient.post(url, mockInputJson);

			GlobalMethods.reportResponse(response.getHeaders().asList().toString(), url, response);

		} else {
			isWaitRequired = true;
			testCaseDTO.setInputTemplate(AdminTestUtil.modifySchemaGenerateHbs(testCaseDTO.isRegenerateHbs()));
			String uin = JsonPrecondtion.getValueFromJson(
					RestClient.getRequestWithCookie(ApplnURI + "/v1/idgenerator/uin", MediaType.APPLICATION_JSON,
							MediaType.APPLICATION_JSON, COOKIENAME,
							new KernelAuthentication().getTokenByRole(testCaseDTO.getRole())).asString(),
					"response.uin");

			DateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
			Calendar cal = Calendar.getInstance();
			String timestampValue = dateFormatter.format(cal.getTime());
			String genRid = "27847" + generateRandomNumberString(10) + timestampValue;

			String jsonInput = testCaseDTO.getInput();

			String inputJson = getJsonFromTemplate(jsonInput, testCaseDTO.getInputTemplate(), false);

			inputJson = inputJson.replace("$UIN$", uin);
			inputJson = inputJson.replace("$RID$", genRid);
			String phoneNumber = "";
			String email = testCaseName + "_" + generateRandomAlphaNumericString(3)
					+ generateRandomAlphaNumericString(3) + "@mosip.net";
			if (inputJson.contains("$PHONENUMBERFORIDENTITY$")) {
				if (!phoneSchemaRegex.isEmpty())
					try {
						phoneNumber = genStringAsperRegex(phoneSchemaRegex);
					} catch (Exception e) {
						logger.error(e.getMessage());
					}
				inputJson = replaceKeywordWithValue(inputJson, "$PHONENUMBERFORIDENTITY$", phoneNumber);
			}
			if (inputJson.contains("$EMAILVALUE$")) {
				inputJson = replaceKeywordWithValue(inputJson, "$EMAILVALUE$", email);
			}

			inputJson = EsignetUtil.inputstringKeyWordHandler(inputJson, testCaseName);

			response = postWithBodyAndCookie(ApplnURI + testCaseDTO.getEndPoint(), inputJson, COOKIENAME,
					testCaseDTO.getRole(), testCaseDTO.getTestCaseName());

			if (testCaseDTO.getTestCaseName().contains("_Pos")) {
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "UIN", uin);
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "RID", genRid);
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "EMAIL", email);
				writeAutoGeneratedId(testCaseDTO.getTestCaseName(), "PHONE", phoneNumber);
			}
		}

		Map<String, List<OutputValidationDto>> outputValid = OutputValidationUtil.doJsonOutputValidation(
				response.asString(), getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate()),
				testCaseDTO, response.getStatusCode());
		Reporter.log(ReportUtil.getOutputValidationReport(outputValid));

		if (!OutputValidationUtil.publishOutputResult(outputValid))
			throw new AdminTestException("Failed at output validation");

		if (isMockIdentitySystem) {
			extractAndStoreMockIdentityDetails(testCaseName, mockInputJson);
		}

	}

	/**
	 * The method ser current test name to result
	 *
	 * @param result
	 */
	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		result.setAttribute("TestCaseName", testCaseName);
	}

	@AfterClass(alwaysRun = true)
	public void waittime() {

		try {
			if (getPluginName().equals("mosipid") == true && isWaitRequired == true) {
				logger.info(
						"waiting for " + EsignetConfigManager.getProperty("DelayInMilliSecAfterUinGeneration", "90000")
								+ " mili secs after UIN Generation In IDREPO");
				Thread.sleep(
						Long.parseLong(EsignetConfigManager.getProperty("DelayInMilliSecAfterUinGeneration", "90000")));
			}

		} catch (Exception e) {
			logger.error("Exception : " + e.getMessage());
			Thread.currentThread().interrupt();
		}
	}
}