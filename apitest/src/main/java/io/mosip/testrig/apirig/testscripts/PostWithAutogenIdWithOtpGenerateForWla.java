package io.mosip.testrig.apirig.testscripts;

import java.lang.reflect.Field;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.internal.BaseTestMethod;
import org.testng.internal.TestResult;

import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.AuthenticationTestException;
import io.mosip.testrig.apirig.utils.ConfigManager;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.restassured.response.Response;

public class PostWithAutogenIdWithOtpGenerateForWla extends AdminTestUtil implements ITest {
	private static final Logger logger = Logger.getLogger(PostWithAutogenIdWithOtpGenerateForWla.class);
	protected String testCaseName = "";
	public String idKeyName = null;
	public Response response = null;

	@BeforeClass
	public static void setLogLevel() {
		if (ConfigManager.IsDebugEnabled())
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
		idKeyName = context.getCurrentXmlTest().getLocalParameters().get("idKeyName");
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
	public void test(TestCaseDTO testCaseDTO) throws AdminTestException {
		testCaseName = testCaseDTO.getTestCaseName();
		if (HealthChecker.signalTerminateExecution) {
			throw new SkipException(
					GlobalConstants.TARGET_ENV_HEALTH_CHECK_FAILED + HealthChecker.healthCheckFailureMapS);
		}
		if (testCaseDTO.getTestCaseName().contains("VID") || testCaseDTO.getTestCaseName().contains("Vid")) {
			if (!BaseTestCase.getSupportedIdTypesValueFromActuator().contains("VID")
					&& !BaseTestCase.getSupportedIdTypesValueFromActuator().contains("vid")) {
				throw new SkipException(GlobalConstants.VID_FEATURE_NOT_SUPPORTED);
			}
		}

		testCaseName = isTestCaseValidForExecution(testCaseDTO);
		JSONObject req = new JSONObject(testCaseDTO.getInput());
		String otpRequest = null;
		String sendOtpReqTemplate = null;
		String sendOtpEndPoint = null;
		if (req.has(GlobalConstants.SENDOTP)) {
			otpRequest = req.get(GlobalConstants.SENDOTP).toString();
			req.remove(GlobalConstants.SENDOTP);
		}
		JSONObject otpReqJson = new JSONObject(otpRequest);
		sendOtpReqTemplate = otpReqJson.getString("sendOtpReqTemplate");
		otpReqJson.remove("sendOtpReqTemplate");
		sendOtpEndPoint = otpReqJson.getString("sendOtpEndPoint");
		otpReqJson.remove("sendOtpEndPoint");

		Response otpResponse = null;
		if (testCaseName.contains(GlobalConstants.ESIGNET_)) {
			String tempUrl = ConfigManager.getEsignetBaseUrl();
			otpResponse = postRequestWithCookieAuthHeaderForAutoGenId(tempUrl + sendOtpEndPoint,
					getJsonFromTemplate(otpReqJson.toString(), sendOtpReqTemplate), COOKIENAME, "mobileauth",
					testCaseDTO.getTestCaseName(), idKeyName);
		} else {
			otpResponse = postWithBodyAndCookie(ApplnURI + sendOtpEndPoint,
					getJsonFromTemplate(otpReqJson.toString(), sendOtpReqTemplate), COOKIENAME,
					GlobalConstants.RESIDENT, testCaseDTO.getTestCaseName());
		}

		JSONObject res = new JSONObject(testCaseDTO.getOutput());
		String sendOtpResp = null;
		String sendOtpResTemplate = null;
		if (res.has(GlobalConstants.SENDOTPRESP)) {
			sendOtpResp = res.get(GlobalConstants.SENDOTPRESP).toString();
			res.remove(GlobalConstants.SENDOTPRESP);
		}
		JSONObject sendOtpRespJson = new JSONObject(sendOtpResp);
		sendOtpResTemplate = sendOtpRespJson.getString("sendOtpResTemplate");
		sendOtpRespJson.remove("sendOtpResTemplate");
		Map<String, List<OutputValidationDto>> ouputValidOtp = OutputValidationUtil.doJsonOutputValidation(
				otpResponse.asString(), getJsonFromTemplate(sendOtpRespJson.toString(), sendOtpResTemplate),
				testCaseDTO, otpResponse.getStatusCode());
		Reporter.log(ReportUtil.getOutputValidationReport(ouputValidOtp));

		if (!OutputValidationUtil.publishOutputResult(ouputValidOtp))
			throw new AdminTestException("Failed at otp output validation");

		String otpValidationRequest = null;
		String validateOtpReqTemplate = null;
		String validateOtpEndPoint = null;

		if (req.has(GlobalConstants.VALIDATEOTP)) {
			otpValidationRequest = req.get(GlobalConstants.VALIDATEOTP).toString();
			req.remove(GlobalConstants.VALIDATEOTP);
		}
		JSONObject validateOtpReqJson = new JSONObject(otpValidationRequest);
		validateOtpReqTemplate = validateOtpReqJson.getString("validateOtpReqTemplate");
		validateOtpReqJson.remove("validateOtpReqTemplate");
		validateOtpEndPoint = validateOtpReqJson.getString("validateOtpEndPoint");
		validateOtpReqJson.remove("validateOtpEndPoint");

		Response validateOtpResponse;
		if (testCaseName.contains(GlobalConstants.ESIGNET_)) {
			String tempUrl = ConfigManager.getEsignetBaseUrl();
			validateOtpResponse = postRequestWithCookieAuthHeaderForAutoGenId(tempUrl + validateOtpEndPoint,
					getJsonFromTemplate(validateOtpReqJson.toString(), validateOtpReqTemplate), COOKIENAME,
					"mobileauth", testCaseDTO.getTestCaseName(), idKeyName);
		}

		if (testCaseName.contains(GlobalConstants.ESIGNET_)) {
			String tempUrl = ConfigManager.getEsignetBaseUrl();
			response = postWithBodyAndCookieAuthHeaderAndXsrfTokenForAutoGeneratedId(
					tempUrl + testCaseDTO.getEndPoint(),
					getJsonFromTemplate(req.toString(), testCaseDTO.getInputTemplate()), COOKIENAME,
					testCaseDTO.getTestCaseName(), idKeyName);
		} else {
			response = postWithBodyAndCookieForAutoGeneratedId(ApplnURI + testCaseDTO.getEndPoint(),
					getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate()), COOKIENAME,
					testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), idKeyName);
		}

		Map<String, List<OutputValidationDto>> ouputValid = OutputValidationUtil.doJsonOutputValidation(
				response.asString(), getJsonFromTemplate(res.toString(), testCaseDTO.getOutputTemplate()), testCaseDTO,
				response.getStatusCode());
		Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));

		if (!OutputValidationUtil.publishOutputResult(ouputValid))
			throw new AdminTestException("Failed at output validation");

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

	@AfterClass(alwaysRun = true)
	public void waittime() {
		try {
			if (!testCaseName.contains(GlobalConstants.ESIGNET_)) {
				logger.info("waiting for" + properties.getProperty("Delaytime")
						+ " mili secs after VID Generation In RESIDENT SERVICES");
				Thread.sleep(Long.parseLong(properties.getProperty("Delaytime")));
			}
		} catch (Exception e) {
			logger.error("Exception : " + e.getMessage());
			Thread.currentThread().interrupt();
		}

	}
}
