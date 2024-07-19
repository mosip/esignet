package io.mosip.testrig.apirig.testscripts;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
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
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.AuthUtil;
import io.mosip.testrig.apirig.utils.AuthenticationTestException;
import io.mosip.testrig.apirig.utils.ConfigManager;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.PartnerTypes;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.restassured.response.Response;

public class PostWithBodyAndQueryParamsForAutoGenId extends AdminTestUtil implements ITest {
	private static final Logger logger = Logger.getLogger(PostWithBodyAndQueryParamsForAutoGenId.class);
	protected String testCaseName = "";
	String queryParams = null;
	public String idKeyName = null;
	public Response response = null;

	@BeforeClass
	public static void setLogLevel() {
		if (ConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	@Override
	public String getTestName() {
		return testCaseName;
	}

	@DataProvider(name = "testcaselist")
	public Object[] getTestCaseList(ITestContext context) {
		String ymlFile = context.getCurrentXmlTest().getLocalParameters().get("ymlFile");
		idKeyName = context.getCurrentXmlTest().getLocalParameters().get("idKeyName");
		queryParams = context.getCurrentXmlTest().getLocalParameters().get("queryParams");
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO) throws AuthenticationTestException, AdminTestException {
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
		
		String inputJson = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());
		
		inputJson = inputJsonKeyWordHandeler(inputJson, testCaseName);
		
		JSONObject requestJson = new JSONObject(inputJson);
		HashMap<String, String> requestBody = new HashMap<>();
		String partnerId = null, certValueSigned =null, moduleName = null, partnerType = null;
		PartnerTypes partnerTypeEnum = null;
		boolean keyFileNameByPartnerName = false;
		if (requestJson.has("partnerType")) {
			partnerType = requestJson.get("partnerType").toString();
			requestJson.remove("partnerType");
		}
		
		if (partnerType.equals("RELYING_PARTY")) {
			partnerTypeEnum = PartnerTypes.RELYING_PARTY;           
        } else if (partnerType.equals("DEVICE")) {
        	partnerTypeEnum = PartnerTypes.DEVICE;
        }else if (partnerType.equals("FTM")) {
        	partnerTypeEnum = PartnerTypes.FTM;
        }else if (partnerType.equals("EKYC")) {
        	partnerTypeEnum = PartnerTypes.EKYC;
        }else if (partnerType.equals("MISP")) {
        	partnerTypeEnum = PartnerTypes.MISP;
        }
		
		if (requestJson.has("partnerName")) {
			partnerId = requestJson.get("partnerName").toString();
			requestJson.remove("partnerName");
		}
		
		if (requestJson.has("moduleName")) {
			moduleName = requestJson.get("moduleName").toString();
			requestJson.remove("moduleName");
		}
		
		if (requestJson.has("keyFileNameByPartnerName")) {
			keyFileNameByPartnerName = requestJson.get("keyFileNameByPartnerName").toString().equals("true");
			requestJson.remove("keyFileNameByPartnerName");
		}
		
		if (requestJson.has("certData")) {
			certValueSigned = requestJson.get("certData").toString();
			requestJson.remove("certData");
		}
		
		requestBody.put("certData", certValueSigned);
		
		AuthUtil authUtil = new AuthUtil();
		
		String str;
		try {
			str = authUtil.updatePartnerCertificate(partnerTypeEnum, partnerId, keyFileNameByPartnerName, requestBody,
					null, moduleName, ApplnURI.replace("https://", ""));
		} catch (Exception e) {
			throw new AdminTestException("Failed to Update Partner Certificate");
		}
		logger.info("Is update partner certificate " + str);

//		if (testCaseName.contains("_AuthDemoUrl_")) {
//			String url = ConfigManager.getAuthDemoServiceUrl();
//
//			logger.info("******Post request Json to EndPointUrl: " + url + testCaseDTO.getEndPoint() + " *******");
//
//			response = postWithQueryParamsBodyAndCookie(url + testCaseDTO.getEndPoint(),
//					getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate()), COOKIENAME,
//					testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), queryParams, idKeyName);
//		} else {
//			logger.info("Not Getting" + GlobalConstants.POST_REQ_URL + testCaseDTO.getTestCaseName() + " *******");
//		}

		Map<String, List<OutputValidationDto>> ouputValid = null;
		if (testCaseName.contains("_StatusCode")) {
			OutputValidationDto customResponse = null;
			if (testCaseName.contains("updatePartnerCertificate_StatusCode_")) {
				customResponse = customStatusCodeResponse("200", testCaseDTO.getOutput());
			} else {
				customResponse = customStatusCodeResponse(String.valueOf(response.getStatusCode()),
						testCaseDTO.getOutput());
			}
			ouputValid = new HashMap<>();
			ouputValid.put(GlobalConstants.EXPECTED_VS_ACTUAL, List.of(customResponse));
		} else {
			ouputValid = OutputValidationUtil.doJsonOutputValidation(response.asString(),
					getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate()), testCaseDTO,
					response.getStatusCode());
		}
		Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));

		if (!OutputValidationUtil.publishOutputResult(ouputValid))
			throw new AdminTestException("Failed at output validation");

	}

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
