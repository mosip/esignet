package io.mosip.testrig.apirig.esignet.testscripts;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.internal.BaseTestMethod;
import org.testng.internal.TestResult;

import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.esignet.utils.EsignetConfigManager;
import io.mosip.testrig.apirig.esignet.utils.EsignetUtil;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.AuthenticationTestException;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.restassured.response.Response;

public class SimplePost extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(SimplePost.class);
	protected String testCaseName = "";
	public Response response = null;
	public boolean sendEsignetToken = false;
	public boolean auditLogCheck = false;

	@BeforeClass
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	/**
	 * get current testcaseName
	 */
	@Override
	public String getTestName() {
		return testCaseName;
	}

	/**
	 * Data provider class provides test case list
	 * 
	 * @return object of data provider
	 */
	@DataProvider(name = "testcaselist")
	public Object[] getTestCaseList(ITestContext context) {
		String ymlFile = context.getCurrentXmlTest().getLocalParameters().get("ymlFile");
		sendEsignetToken = context.getCurrentXmlTest().getLocalParameters().containsKey("sendEsignetToken");
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	/**
	 * Test method for OTP Generation execution
	 * 
	 * @param objTestParameters
	 * @param testScenario
	 * @param testcaseName
	 * @throws AuthenticationTestException
	 * @throws AdminTestException
	 */
	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO) throws AuthenticationTestException, AdminTestException {
		testCaseName = testCaseDTO.getTestCaseName();
		testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);
		auditLogCheck = testCaseDTO.isAuditLogCheck();
		String[] templateFields = testCaseDTO.getTemplateFields();
		if (HealthChecker.signalTerminateExecution) {
			throw new SkipException(
					GlobalConstants.TARGET_ENV_HEALTH_CHECK_FAILED + HealthChecker.healthCheckFailureMapS);
		}

		if (testCaseDTO.getTestCaseName().contains("VID") || testCaseDTO.getTestCaseName().contains("Vid")) {
			if (!BaseTestCase.getSupportedIdTypesValue().contains("VID")
					&& !BaseTestCase.getSupportedIdTypesValue().contains("vid")) {
				throw new SkipException(GlobalConstants.VID_FEATURE_NOT_SUPPORTED);
			}
		}

		String inputJson = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());

		if (testCaseDTO.getTemplateFields() != null && templateFields.length > 0) {
			ArrayList<JSONObject> inputtestCases = AdminTestUtil.getInputTestCase(testCaseDTO);
			ArrayList<JSONObject> outputtestcase = AdminTestUtil.getOutputTestCase(testCaseDTO);
			for (int i = 0; i < languageList.size(); i++) {
				response = postWithBodyAndCookie(ApplnURI + testCaseDTO.getEndPoint(),
						getJsonFromTemplate(inputtestCases.get(i).toString(), testCaseDTO.getInputTemplate()),
						COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName());

				Map<String, List<OutputValidationDto>> ouputValid = OutputValidationUtil.doJsonOutputValidation(
						response.asString(),
						getJsonFromTemplate(outputtestcase.get(i).toString(), testCaseDTO.getOutputTemplate()),
						testCaseDTO, response.getStatusCode());
				Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));

				if (!OutputValidationUtil.publishOutputResult(ouputValid))
					throw new AdminTestException("Failed at output validation");
			}
		}

		else {
			String tempUrl = EsignetConfigManager.getEsignetBaseUrl();
			if (testCaseDTO.getEndPoint().contains("/signup/"))
				tempUrl = EsignetConfigManager.getSignupBaseUrl();
			if (testCaseName.contains("ESignet_")) {

				if ((testCaseDTO.getEndPoint().startsWith("$ESIGNETMOCKBASEURL$") && testCaseName.contains("SunBirdC"))
						|| (testCaseDTO.getEndPoint().startsWith("$SUNBIRDBASEURL$")
								&& testCaseName.contains("SunBirdR"))) {
					if (EsignetConfigManager.isInServiceNotDeployedList("sunbirdrc"))
						throw new SkipException(GlobalConstants.SERVICE_NOT_DEPLOYED_MESSAGE);
				}

				inputJson = EsignetUtil.inputstringKeyWordHandeler(inputJson, testCaseName);

				if (testCaseDTO.getEndPoint().startsWith("$ESIGNETMOCKBASEURL$") && testCaseName.contains("SunBirdC")) {

					if (EsignetConfigManager.getEsignetMockBaseURL() != null
							&& !EsignetConfigManager.getEsignetMockBaseURL().isBlank())
						tempUrl = ApplnURI.replace("api-internal.", EsignetConfigManager.getEsignetMockBaseURL());
					testCaseDTO.setEndPoint(testCaseDTO.getEndPoint().replace("$ESIGNETMOCKBASEURL$", ""));

					response = postRequestWithCookieAuthHeaderAndXsrfToken(tempUrl + testCaseDTO.getEndPoint(),
							inputJson, COOKIENAME, testCaseDTO.getTestCaseName());
				} else if (testCaseDTO.getEndPoint().startsWith("$SUNBIRDBASEURL$")
						&& testCaseName.contains("SunBirdR")) {

					if (EsignetConfigManager.getSunBirdBaseURL() != null
							&& !EsignetConfigManager.getSunBirdBaseURL().isBlank())
						tempUrl = EsignetConfigManager.getSunBirdBaseURL();
					testCaseDTO.setEndPoint(testCaseDTO.getEndPoint().replace("$SUNBIRDBASEURL$", ""));

					response = postWithBodyAndCookie(tempUrl + testCaseDTO.getEndPoint(), inputJson, auditLogCheck,
							COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), sendEsignetToken);

				} else if (testCaseName.contains("ESignet_SendBindingOtp")) {

					if (EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase()
							.contains("mockauthenticationservice") == true) {
						inputJson = inputJsonKeyWordHandeler(inputJson, testCaseName);
						response = EsignetUtil.postRequestWithCookieAndAuthHeader(tempUrl + testCaseDTO.getEndPoint(),
								inputJson, COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName());
					} else {
						response = postRequestWithCookieAuthHeader(tempUrl + testCaseDTO.getEndPoint(), inputJson,
								COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName());
					}

				} else {
					response = postRequestWithCookieAuthHeaderAndXsrfToken(tempUrl + testCaseDTO.getEndPoint(),
							inputJson, COOKIENAME, testCaseDTO.getTestCaseName());

				}
			} else {
				inputJson = EsignetUtil.inputstringKeyWordHandeler(inputJson, testCaseName);
				response = postWithBodyAndCookie(ApplnURI + testCaseDTO.getEndPoint(), inputJson, auditLogCheck,
						COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), sendEsignetToken);
			}
			Map<String, List<OutputValidationDto>> ouputValid = null;
			if (testCaseName.contains("_StatusCode")) {

				OutputValidationDto customResponse = customStatusCodeResponse(String.valueOf(response.getStatusCode()),
						testCaseDTO.getOutput());

				ouputValid = new HashMap<>();
				ouputValid.put(GlobalConstants.EXPECTED_VS_ACTUAL, List.of(customResponse));
			} else {
				ouputValid = OutputValidationUtil.doJsonOutputValidation(response.asString(),
						getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate()), testCaseDTO,
						response.getStatusCode());
			}

			Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));

			if (!OutputValidationUtil.publishOutputResult(ouputValid)) {
				if (response.asString().contains("IDA-OTA-001"))
					throw new AdminTestException(
							"Exceeded number of OTP requests in a given time, Increase otp.request.flooding.max-count");
				else
					throw new AdminTestException("Failed at otp output validation");
			}
		}

	}

	/**
	 * The method ser current test name to result
	 * 
	 * @param result
	 */
	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		try {
			Field method = TestResult.class.getDeclaredField("m_method");
			method.setAccessible(true);
			method.set(result, result.getMethod().clone());
			BaseTestMethod baseTestMethod = (BaseTestMethod) result.getMethod();
			Field f = baseTestMethod.getClass().getSuperclass().getDeclaredField("m_methodName");
			f.setAccessible(true);
			f.set(baseTestMethod, testCaseName);
		} catch (Exception e) {
			Reporter.log("Exception : " + e.getMessage());
		}
	}
}
