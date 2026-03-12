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

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

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
import io.mosip.testrig.apirig.utils.GlobalMethods;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.mosip.testrig.apirig.utils.SecurityXSSException;
import io.restassured.response.Response;

public class GetWithParam extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(GetWithParam.class);
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
	public void test(TestCaseDTO testCaseDTO) throws AuthenticationTestException, AdminTestException, SecurityXSSException {
		testCaseName = testCaseDTO.getTestCaseName();
		testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);
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
		auditLogCheck = testCaseDTO.isAuditLogCheck();
		String[] templateFields = testCaseDTO.getTemplateFields();

		if (testCaseDTO.getInputTemplate().contains(GlobalConstants.$PRIMARYLANG$))
			testCaseDTO.setInputTemplate(testCaseDTO.getInputTemplate().replace(GlobalConstants.$PRIMARYLANG$,
					BaseTestCase.languageList.get(0)));
		if (testCaseDTO.getOutputTemplate().contains(GlobalConstants.$PRIMARYLANG$))
			testCaseDTO.setOutputTemplate(testCaseDTO.getOutputTemplate().replace(GlobalConstants.$PRIMARYLANG$,
					BaseTestCase.languageList.get(0)));
		if (testCaseDTO.getInput().contains(GlobalConstants.$PRIMARYLANG$))
			testCaseDTO.setInput(
					testCaseDTO.getInput().replace(GlobalConstants.$PRIMARYLANG$, BaseTestCase.languageList.get(0)));
		if (testCaseDTO.getOutput().contains(GlobalConstants.$PRIMARYLANG$))
			testCaseDTO.setOutput(
					testCaseDTO.getOutput().replace(GlobalConstants.$PRIMARYLANG$, BaseTestCase.languageList.get(0)));

		if (testCaseDTO.getTemplateFields() != null && templateFields.length > 0) {
			ArrayList<JSONObject> inputtestCases = AdminTestUtil.getInputTestCase(testCaseDTO);
			ArrayList<JSONObject> outputtestcase = AdminTestUtil.getOutputTestCase(testCaseDTO);
			for (int i = 0; i < languageList.size(); i++) {
				response = getWithPathParamAndCookie(ApplnURI + testCaseDTO.getEndPoint(),
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
			String inputJson = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());

			if (testCaseName.contains("ESignet_")) {
				String tempUrl = EsignetConfigManager.getEsignetBaseUrl();
				if (testCaseDTO.getEndPoint().contains("/signup/"))
					tempUrl = EsignetConfigManager.getSignupBaseUrl();

				if (testCaseDTO.getEndPoint().startsWith("$SUNBIRDBASEURL$") && testCaseName.contains("SunBirdR")) {

					if (EsignetConfigManager.isInServiceNotDeployedList("sunbirdrc"))
						throw new SkipException(GlobalConstants.SERVICE_NOT_DEPLOYED_MESSAGE);

					if (EsignetConfigManager.getSunBirdBaseURL() != null
							&& !EsignetConfigManager.getSunBirdBaseURL().isBlank())
						tempUrl = EsignetConfigManager.getSunBirdBaseURL();
					testCaseDTO.setEndPoint(testCaseDTO.getEndPoint().replace("$SUNBIRDBASEURL$", ""));
				}
				inputJson = EsignetUtil.inputstringKeyWordHandeler(inputJson, testCaseName);
				if (testCaseName.contains("_AuthToken_Xsrf_")) {
					response = getRequestWithCookieAuthHeaderAndXsrfToken(tempUrl + testCaseDTO.getEndPoint(),
							inputJson, COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName());
				} else if (testCaseName.contains("_Dpop_AccessToken_")) {
					response = getRequestWithHeaders(tempUrl + testCaseDTO.getEndPoint(), inputJson,
							testCaseDTO.getTestCaseName());
					if (testCaseName.toLowerCase().contains("_smoke") && response != null
							&& response.getStatusCode() == 401 && hasDpopNonce(response)) {
						logger.info("use_dpop_nonce detected in response for test case: " + testCaseName);
						saveDpopNonce(response, testCaseName);
						inputJson = inputstringKeyWordHandeler(
								getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate()),
								testCaseName);
						response = getRequestWithHeaders(tempUrl + testCaseDTO.getEndPoint(), inputJson,
								testCaseDTO.getTestCaseName());
					}
				} else {
					response = getWithPathParamAndCookie(tempUrl + testCaseDTO.getEndPoint(), inputJson, COOKIENAME,
							testCaseDTO.getRole(), testCaseDTO.getTestCaseName());
				}
			} else {
				inputJson = EsignetUtil.inputstringKeyWordHandeler(inputJson, testCaseName);
				response = getWithPathParamAndCookie(ApplnURI + testCaseDTO.getEndPoint(), inputJson, auditLogCheck,
						COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), sendEsignetToken);
			}
			
			String responseBody = response.asString();
			Map<String, List<OutputValidationDto>> ouputValid = null;

			String outputJson = getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate());

			if (outputJson != null) {
				outputJson = EsignetUtil.inputstringKeyWordHandeler(outputJson, testCaseName);
			}

			if (testCaseName.contains("_StatusCode")) {

				OutputValidationDto customResponse = customStatusCodeResponse(String.valueOf(response.getStatusCode()),
						testCaseDTO.getOutput());
				ouputValid = new HashMap<>();
				ouputValid.put(GlobalConstants.EXPECTED_VS_ACTUAL, List.of(customResponse));
			} else if (testCaseName.contains("_GetUserInfoJWS_")) {

				if (responseBody == null || responseBody.isEmpty()) {
					throw new AdminTestException("Response body is empty");
				}

				String finalJsonString = AdminTestUtil.decodeAndCombineJwt(responseBody);

				if (finalJsonString == null) {
					throw new AdminTestException("Failed to decode JWS response");
				}

				DecodedJWT jwt = JWT.decode(responseBody);
				String headerJson = AdminTestUtil.decodeBase64Url(jwt.getHeader());
				String payloadJson = AdminTestUtil.decodeBase64Url(jwt.getPayload());

				GlobalMethods.reportResponse(headerJson, null, payloadJson, true);

				ouputValid = OutputValidationUtil.doJsonOutputValidation(finalJsonString, outputJson, testCaseDTO,
						response.getStatusCode());

			} else {

				ouputValid = OutputValidationUtil.doJsonOutputValidation(response.asString(), outputJson, testCaseDTO,
						response.getStatusCode());
			}

			Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));
			if (!OutputValidationUtil.publishOutputResult(ouputValid))
				throw new AdminTestException("Failed at output validation");
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
}
