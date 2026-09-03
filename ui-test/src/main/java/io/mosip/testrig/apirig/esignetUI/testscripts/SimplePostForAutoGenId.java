package io.mosip.testrig.apirig.esignetUI.testscripts;

import java.security.NoSuchAlgorithmException;
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

import constants.ESignetConstants;
import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AuthenticationTestException;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.mosip.testrig.apirig.utils.SecurityXSSException;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.restassured.response.Response;
import utils.EsignetConfigManager;
import utils.EsignetUtil;

public class SimplePostForAutoGenId extends EsignetUtil implements ITest {
	private static final Logger logger = Logger.getLogger(SimplePostForAutoGenId.class);
	protected String testCaseName = "";
	public String idKeyName = null;
	public Response response = null;
	public boolean sendEsignetToken = false;
	public boolean auditLogCheck = false;

	private static final int SUNBIRD_CREATE_MAX_RETRIES = 10;
	private static final long SUNBIRD_CREATE_RETRY_BACKOFF_MS = 500;

	@BeforeClass
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
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
		sendEsignetToken = context.getCurrentXmlTest().getLocalParameters().containsKey("sendEsignetToken");
		idKeyName = context.getCurrentXmlTest().getLocalParameters().get("idKeyName");
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO)
			throws AuthenticationTestException, AdminTestException, NoSuchAlgorithmException, SecurityXSSException {
		testCaseName = testCaseDTO.getTestCaseName();
		testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);

		if (HealthChecker.signalTerminateExecution) {
			throw new SkipException(
					GlobalConstants.TARGET_ENV_HEALTH_CHECK_FAILED + HealthChecker.healthCheckFailureMapS);
		}

		if ("VID".equals(idKeyName)) {
			writeConfigValueAndSkipIfProvided("vid", testCaseName, idKeyName);
		}

		if ("clientId".equals(idKeyName) && testCaseDTO.getEndPoint().contains("/v1/esignet/client-mgmt/client")) {
			boolean isSunbirdClientVariant = testCaseDTO.getInputTemplate().contains("SunBird");
			boolean sunbirdActive = EsignetUtil.isSunbirdAuthenticatorActive();

			if (isSunbirdClientVariant && !sunbirdActive) {
				throw new SkipException("Skipped: " + testCaseName + " is only needed on a Sunbird RC-backed server");
			}
			if (!isSunbirdClientVariant && (sunbirdActive || !"mock".equalsIgnoreCase(getPluginName()))) {
				throw new SkipException("Skipped: " + testCaseName + " is only needed for the non-Sunbird mock plugin");
			}
		}

		if ("clientId".equals(idKeyName) && testCaseName.contains("CreateOIDCClient_all_Valid_Smoke_sid")) {
			writeConfigValueAndSkipIfProvided("oidcClientId", testCaseName, idKeyName);
		}

		if ("clientId".equals(idKeyName) && testCaseName.contains("CreateOIDCClient_secondary_Smoke_sid")) {
			writeSecondaryConfigValueAndSkipIfProvided("oidcClientId", testCaseName, idKeyName);
		}

		String inputJson = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());

		if (testCaseName.contains(ESignetConstants.ESIGNET_STRING)) {
			if (EsignetConfigManager.isInServiceNotDeployedList(GlobalConstants.ESIGNET)) {
				throw new SkipException("esignet is not deployed hence skipping the testcase");
			}
			String tempUrl = null;
			tempUrl = EsignetConfigManager.getEsignetBaseUrl();

			boolean isSunbirdPolicy = testCaseDTO.getEndPoint().startsWith("$SUNBIRDBASEURL$");
			if (isSunbirdPolicy) {
				if (!EsignetUtil.isSunbirdAuthenticatorActive()) {
					throw new SkipException(
							"Skipped: " + testCaseName + " requires the Sunbird RC authenticator to be active on the server");
				}
				tempUrl = EsignetConfigManager.getSunBirdBaseURL();
				testCaseDTO.setEndPoint(testCaseDTO.getEndPoint().replace("$SUNBIRDBASEURL$", ""));
			}

			inputJson = EsignetUtil.inputstringKeyWordHandler(inputJson, testCaseName);
			if (isSunbirdPolicy || getPluginName().equals("mock") == true) {
				if (!isSunbirdPolicy) {
					inputJson = inputJsonKeyWordHandeler(inputJson, testCaseName);
				}
				if (isSunbirdPolicy) {

					int currLoopCount = 0;
					do {
						response = EsignetUtil.postWithBodyAndBearerToken(tempUrl + testCaseDTO.getEndPoint(), inputJson,
								COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), idKeyName);
						if (response != null && !response.asString().contains("UNSUCCESSFUL")) {
							break;
						}

						if (response == null) {
							logger.error(testCaseName + ": Sunbird RC create returned no response - the policy may or "
									+ "may not exist. Not retrying, to avoid an undeletable duplicate record. "
									+ "Verify the registry manually.");
							break;
						}
						currLoopCount++;
						if (currLoopCount < SUNBIRD_CREATE_MAX_RETRIES) {
							try {
								Thread.sleep(Math.min(currLoopCount, 5) * SUNBIRD_CREATE_RETRY_BACKOFF_MS);
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								break;
							}
						}
					} while (currLoopCount < SUNBIRD_CREATE_MAX_RETRIES);
				} else {
					response = EsignetUtil.postWithBodyAndBearerToken(tempUrl + testCaseDTO.getEndPoint(), inputJson,
							COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), idKeyName);
				}

				boolean isSuccessResponse = response != null && response.getStatusCode() >= 200
						&& response.getStatusCode() < 300;
				if (isSunbirdPolicy) {
					if (isSuccessResponse) {
						String osid = extractSunbirdOsid(new JSONObject(response.getBody().asString()));

						if (osid == null || osid.isBlank()) {
							throw new AdminTestException(
									"Sunbird RC create succeeded but the response carried no osid: " + response.asString());
						}
						writeAutoGeneratedId(testCaseName, idKeyName, osid);
					}
				} else if (testCaseName.toLowerCase().contains("_sid") && isSuccessResponse) {
					writeAutoGeneratedId(testCaseName, idKeyName, new JSONObject(response.getBody().asString())
							.getJSONObject(GlobalConstants.RESPONSE).getString(idKeyName).toString());
				}
			} else {
				response = postWithBodyAndBearerTokenForAutoGeneratedId(tempUrl + testCaseDTO.getEndPoint(), inputJson,
						COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), idKeyName);
			}

		} else {
			inputJson = EsignetUtil.inputstringKeyWordHandler(inputJson, testCaseName);
			response = postWithBodyAndCookieForAutoGeneratedId(ApplnURI + testCaseDTO.getEndPoint(), inputJson,
					auditLogCheck, COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), idKeyName,
					sendEsignetToken);
		}

		if (response == null) {
			throw new AdminTestException(testCaseName + ": request failed with no response after all attempts");
		}

		Map<String, List<OutputValidationDto>> outputValid = null;
		if (testCaseName.contains("_StatusCode")) {

			OutputValidationDto customResponse = customStatusCodeResponse(String.valueOf(response.getStatusCode()),
					testCaseDTO.getOutput());

			outputValid = new HashMap<>();
			outputValid.put(GlobalConstants.EXPECTED_VS_ACTUAL, List.of(customResponse));
		} else {
			outputValid = OutputValidationUtil.doJsonOutputValidation(response.asString(),
					getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate()), testCaseDTO,
					response.getStatusCode());
		}
		Reporter.log(ReportUtil.getOutputValidationReport(outputValid));
		if (!OutputValidationUtil.publishOutputResult(outputValid))
			throw new AdminTestException("Failed at output validation");

	}

	private String extractSunbirdOsid(JSONObject responseJson) {
		JSONObject result = responseJson.optJSONObject("result");
		if (result == null) {
			return null;
		}
		for (String key : result.keySet()) {
			JSONObject entity = result.optJSONObject(key);
			if (entity != null && entity.has("osid")) {
				return entity.optString("osid", null);
			}
		}
		return null;
	}

	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		result.setTestName(testCaseName);
	}
}
