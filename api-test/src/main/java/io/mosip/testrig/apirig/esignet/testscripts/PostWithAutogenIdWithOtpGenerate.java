package io.mosip.testrig.apirig.esignet.testscripts;

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
import io.mosip.testrig.apirig.utils.SecurityXSSException;
import io.restassured.response.Response;

public class PostWithAutogenIdWithOtpGenerate extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(PostWithAutogenIdWithOtpGenerate.class);
	protected String testCaseName = "";
	public String idKeyName = null;
	public Response response = null;
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
	 * @throws InterruptedException
	 * @throws NumberFormatException
	 */
	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO)
			throws AuthenticationTestException, AdminTestException, NumberFormatException, InterruptedException, SecurityXSSException {
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

		String inputJson = testCaseDTO.getInput().toString();
		JSONObject req = new JSONObject(testCaseDTO.getInput());

		auditLogCheck = testCaseDTO.isAuditLogCheck();
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
		
		String input = getJsonFromTemplate(otpReqJson.toString(), sendOtpReqTemplate);
		
		Response otpResponse = null;
		int maxLoopCount = Integer.parseInt(properties.getProperty("uinGenMaxLoopCount"));
		int currLoopCount = 0;
		while (currLoopCount < maxLoopCount) {
			input = EsignetUtil.inputstringKeyWordHandeler(input, testCaseName);
			if (testCaseName.contains(GlobalConstants.ESIGNET_)) {
				if (EsignetConfigManager.isInServiceNotDeployedList(GlobalConstants.ESIGNET)) {
					throw new SkipException("esignet is not deployed hence skipping the testcase");
				}
				String tempUrl = EsignetConfigManager.getEsignetBaseUrl();

				if (testCaseDTO.getEndPoint().contains("/signup/"))
					tempUrl = EsignetConfigManager.getSignupBaseUrl();
				otpResponse = postRequestWithCookieAuthHeaderAndXsrfToken(tempUrl + sendOtpEndPoint, input, COOKIENAME,
						testCaseDTO.getTestCaseName());
			} else {
				otpResponse = postWithBodyAndCookie(ApplnURI + sendOtpEndPoint, input, COOKIENAME,
						GlobalConstants.RESIDENT, testCaseDTO.getTestCaseName());
			}

			if (otpResponse != null && otpResponse.asString().contains("IDA-MLC-018")) {
				logger.info("waiting for: " + properties.getProperty("uinGenDelayTime")
						+ " as UIN not available in database");
				try {
					Thread.sleep(Long.parseLong(properties.getProperty("uinGenDelayTime")));
				} catch (NumberFormatException | InterruptedException e) {
					logger.error(e.getMessage());
					Thread.currentThread().interrupt();
				}
			} else {
				break;
			}

			currLoopCount++;
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
		if (otpResponse != null) {
			Map<String, List<OutputValidationDto>> ouputValidOtp = OutputValidationUtil.doJsonOutputValidation(
					otpResponse.asString(), getJsonFromTemplate(sendOtpRespJson.toString(), sendOtpResTemplate),
					testCaseDTO, otpResponse.getStatusCode());
			Reporter.log(ReportUtil.getOutputValidationReport(ouputValidOtp));

			if (!OutputValidationUtil.publishOutputResult(ouputValidOtp)) {
				if (otpResponse.asString().contains("IDA-OTA-001")) {
					throw new AdminTestException(
							"Exceeded number of OTP requests in a given time, Increase otp.request.flooding.max-count");
				}

				else
					throw new AdminTestException("Failed at otp output validation");
			}

		} else {
			throw new AdminTestException("Invalid otp response");
		}
		
		String jsonInput = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());
		jsonInput = EsignetUtil.inputstringKeyWordHandeler(jsonInput, testCaseName);

		if (testCaseName.contains(GlobalConstants.ESIGNET_)) {
			if (EsignetConfigManager.isInServiceNotDeployedList(GlobalConstants.ESIGNET)) {
				throw new SkipException("esignet is not deployed hence skipping the testcase");
			}
			String tempUrl = EsignetConfigManager.getEsignetBaseUrl();
			if (testCaseDTO.getEndPoint().contains("/signup/"))
				tempUrl = EsignetConfigManager.getSignupBaseUrl();
			if (testCaseName.startsWith("ESignet_VerifyChallengeNegTC_")
					|| testCaseName.startsWith("ESignet_VerifyChallengeForResetPasswordNegTC_")) {
				response = postRequestWithCookieAuthHeaderAndXsrfToken(tempUrl + testCaseDTO.getEndPoint(), jsonInput,
						COOKIENAME, testCaseDTO.getTestCaseName());
			} else {
				response = postRequestWithCookieAuthHeaderAndXsrfTokenForAutoGenId(tempUrl + testCaseDTO.getEndPoint(),
						jsonInput, COOKIENAME, testCaseDTO.getTestCaseName(), idKeyName);
			}
		} else {
			response = postWithBodyAndCookieForAutoGeneratedId(ApplnURI + testCaseDTO.getEndPoint(), jsonInput,
					auditLogCheck, COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), idKeyName);
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
			if (EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase()
					.contains("mockauthenticationservice") == false
					&& EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase()
							.contains("sunbirdrcauthenticationservice") == false) {
				if (!testCaseName.contains(GlobalConstants.ESIGNET_)) {
					long delayTime = Long.parseLong(properties.getProperty("Delaytime"));
					logger.info("waiting for " + delayTime + " mili secs after VID Generation In RESIDENT SERVICES");
					Thread.sleep(delayTime);
				}
			}

		} catch (Exception e) {
			logger.error("Exception : " + e.getMessage());
			Thread.currentThread().interrupt();
		}

	}
}
