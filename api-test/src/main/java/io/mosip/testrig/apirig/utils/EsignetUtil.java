package io.mosip.testrig.apirig.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.SkipException;

import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.testrunner.MosipTestRunner;
import io.restassured.response.Response;

public class EsignetUtil extends AdminTestUtil {

	private static final Logger logger = Logger.getLogger(EsignetUtil.class);
	
	public static String getIdentityPluginNameFromEsignetActuator() {
		// Possible values = IdaAuthenticatorImpl, MockAuthenticationService

		String plugin = getValueFromEsignetActuator("classpath:/application.properties",
				"mosip.esignet.integration.authenticator");

		if (plugin == null || plugin.isBlank() == true) {
			plugin = getValueFromEsignetActuator("classpath:/application-default.properties",
					"mosip.esignet.integration.authenticator");
		}
		
		if (plugin == null || plugin.isBlank() == true) {
			plugin = getValueFromEsignetActuator("mosip-config/esignet",
					"mosip.esignet.integration.authenticator");
		}

		return plugin;
	}
	
	private static final Map<String, String> actuatorValueCache = new HashMap<>();
	
	public static JSONArray esignetActuatorResponseArray = null;

	public static String getValueFromEsignetActuator(String section, String key) {
		String url = EsignetConfigManager.getEsignetBaseUrl() + EsignetConfigManager.getproperty("actuatorEsignetEndpoint");
		String actuatorCacheKey = url + section + key;
		String value = actuatorValueCache.get(actuatorCacheKey);
		if (value != null && !value.isEmpty())
			return value;

		try {
			if (esignetActuatorResponseArray == null) {
				Response response = null;
				JSONObject responseJson = null;
				response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
				responseJson = new JSONObject(response.getBody().asString());
				esignetActuatorResponseArray = responseJson.getJSONArray("propertySources");
			}

			for (int i = 0, size = esignetActuatorResponseArray.length(); i < size; i++) {
				JSONObject eachJson = esignetActuatorResponseArray.getJSONObject(i);
				if (eachJson.get("name").toString().contains(section)) {
					logger.info(eachJson.getJSONObject(GlobalConstants.PROPERTIES));
					value = eachJson.getJSONObject(GlobalConstants.PROPERTIES).getJSONObject(key)
							.get(GlobalConstants.VALUE).toString();
					if (EsignetConfigManager.IsDebugEnabled())
						logger.info("Actuator: " + url + " key: " + key + " value: " + value);
					break;
				}
			}
			actuatorValueCache.put(actuatorCacheKey, value);

			return value;
		} catch (Exception e) {
			logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			return value;
		}

	}
	
	public static String isTestCaseValidForExecution(TestCaseDTO testCaseDTO) {
		String testCaseName = testCaseDTO.getTestCaseName();
		
		
		if (MosipTestRunner.skipAll == true) {
			throw new SkipException(GlobalConstants.PRE_REQUISITE_FAILED_MESSAGE);
		}
		
		
		if (getIdentityPluginNameFromEsignetActuator().toLowerCase().contains("mockauthenticationservice")) {
			
			// TO DO - need to conform whether esignet distinguishes between UIN and VID. BAsed on that need to remove VID test case from YAML.
			BaseTestCase.setSupportedIdTypes(Arrays.asList("UIN"));
			
			// Let run test cases eSignet & mock (for identity)   -- only UIN  test cases
			
			String endpoint = testCaseDTO.getEndPoint();
			if (endpoint.contains("/esignet/vci/") == true) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}
			if (endpoint.contains("/esignet/vci/") == false && endpoint.contains("/esignet/") == false
					&& endpoint.contains("/v1/signup/") == false && endpoint.contains("/mock-identity-system/") == false
					&& endpoint.contains("$GETENDPOINTFROMWELLKNOWN$") == false) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}
			if ((testCaseName.contains("_KycBioAuth_") || testCaseName.contains("_BioAuth_")
					|| testCaseName.contains("_SendBindingOtp_uin_Email_Valid_Smoke")
					|| testCaseName.contains("ESignet_AuthenticateUserIDP_NonAuth_uin_Otp_Valid_Smoke"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

		} else if (getIdentityPluginNameFromEsignetActuator().toLowerCase().contains("idaauthenticatorimpl")) {
			// Let run test cases eSignet & MOSIP API calls --- both UIN and VID

			BaseTestCase.setSupportedIdTypes(Arrays.asList("UIN", "VID"));

			String endpoint = testCaseDTO.getEndPoint();
			if (endpoint.contains("/v1/signup/") == true || endpoint.contains("/mock-identity-system/") == true
					|| ((testCaseName.equals("ESignet_CreateOIDCClient_all_Valid_Smoke_sid")
							|| testCaseName.equals("ESignet_CreateOIDCClient_Misp_Valid_Smoke_sid")
							|| testCaseName.equals("ESignet_CreateOIDCClient_NonAuth_all_Valid_Smoke_sid"))
							&& endpoint.contains("/v1/esignet/client-mgmt/oauth-client"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

			JSONArray individualBiometricsArray = new JSONArray(
					getValueFromAuthActuator("json-property", "individualBiometrics"));
			String individualBiometrics = individualBiometricsArray.getString(0);

			if ((testCaseName.contains("_KycBioAuth_") || testCaseName.contains("_BioAuth_")
					|| testCaseName.contains("_SendBindingOtp_uin_Email_Valid_Smoke"))
					&& (!isElementPresent(new JSONArray(schemaRequiredField), individualBiometrics))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

		} else if (getIdentityPluginNameFromEsignetActuator().toLowerCase().contains("sunbird")) {
			// Let run test cases eSignet & Sunbird (for identity)   -- only KBI 
			
		}
		
		if (testCaseDTO.isValidityCheckRequired()) {
			if (testCaseName.contains("uin") || testCaseName.contains("UIN") || testCaseName.contains("Uin")) {
				if (BaseTestCase.getSupportedIdTypesValue().contains("UIN")
						&& BaseTestCase.getSupportedIdTypesValue().contains("uin")) {
					throw new SkipException("Idtype UIN not supported skipping the testcase");
				}
			} else if (testCaseName.contains("vid") || testCaseName.contains("VID") || testCaseName.contains("Vid")) {
				if (BaseTestCase.getSupportedIdTypesValue().contains("VID")
						&& BaseTestCase.getSupportedIdTypesValue().contains("vid")) {
					throw new SkipException("Idtype VID not supported skipping the testcase");
				}
			}
		}

		if (SkipTestCaseHandler.isTestCaseInSkippedList(testCaseName)) {
			throw new SkipException(GlobalConstants.KNOWN_ISSUES);
		}

		return testCaseName;
	}
	
	public static String inputstringKeyWordHandeler(String jsonString, String testCaseName) {
		if (jsonString.contains(GlobalConstants.TIMESTAMP))
			jsonString = replaceKeywordValue(jsonString, GlobalConstants.TIMESTAMP, generateCurrentUTCTimeStamp());
		
		
		return jsonString;
		
	}
	
	public static String replaceKeywordValue(String jsonString, String keyword, String value) {
		if (value != null && !value.isEmpty())
			return jsonString.replace(keyword, value);
		else
			throw new SkipException("Marking testcase as skipped as required fields are empty " + keyword);
	}
	
}