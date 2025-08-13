package utils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.core.MediaType;

import io.mosip.testrig.apirig.dbaccess.DBManager;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.RestClient;
import io.restassured.response.Response;
import constants.UiConstants;

public class EsignetUtil extends AdminTestUtil {

	private static final Logger logger = Logger.getLogger(EsignetUtil.class);
	public static String pluginName = null;
	public static JSONArray signupActiveProfiles = null;
	private static String policyNumberForSunBirdR = generateRandomNumberString(9);

	public static List<String> testCasesInRunScope = new ArrayList<>();

	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

//	public static void dBCleanup() {
//		DBManager.executeDBQueries(EsignetConfigManager.getKMDbUrl(), EsignetConfigManager.getKMDbUser(),
//				EsignetConfigManager.getKMDbPass(), EsignetConfigManager.getKMDbSchema(),
//				getGlobalResourcePath() + "/" + "config/keyManagerDataDeleteQueriesForEsignet.txt");
//	}

	public static void dBCleanup() {
		System.out.print("initiated db cleanup operation");
		System.out.println(EsignetConfigManager.getDbUrl());
		try {
			URL resource = EsignetUtil.class.getClassLoader().getResource("config/MockIdentityDataDeleteQueries.txt");
			if (resource == null) {
				throw new RuntimeException(
						"Query file not found in classpath: config/MockIdentityDataDeleteQueriest.txt");
			}
			String queryFilePath = resource.getPath();
			DBManager.executeDBQueries(EsignetConfigManager.getDbUrl(), EsignetConfigManager.getDbUser(),
					EsignetConfigManager.getDbPassword(), EsignetConfigManager.getDbSchema(), queryFilePath);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("DB cleanup failed", e);
		}
	}

	public static JSONArray signupActuatorResponseArray = null;

	public static String getValueFromSignupActuator(String section, String key) {

		String value = null;
		// Normalize the key for environment variables
		String keyForEnvVariableSection = key.toUpperCase().replace("-", "_").replace(".", "_");

		// Try to fetch profiles if not already fetched
		if (signupActiveProfiles == null || signupActiveProfiles.length() == 0) {
			signupActiveProfiles = getActiveProfilesFromActuator(UiConstants.SIGNUP_ACTUATOR_URL,
					UiConstants.ACTIVE_PROFILES);
		}

		// First try to fetch the value from system environment
		value = getValueFromSignupActuatorWithUrl(UiConstants.SYSTEM_ENV_SECTION, keyForEnvVariableSection,
				UiConstants.SIGNUP_ACTUATOR_URL);

		// Fallback to other sections if value is not found
		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(UiConstants.CLASS_PATH_APPLICATION_PROPERTIES, key,
					UiConstants.SIGNUP_ACTUATOR_URL);
		}

		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(UiConstants.CLASS_PATH_APPLICATION_DEFAULT_PROPERTIES, key,
					UiConstants.SIGNUP_ACTUATOR_URL);
		}

		// Try fetching from active profiles if available
		if (value == null || value.isBlank()) {
			if (signupActiveProfiles != null && signupActiveProfiles.length() > 0) {
				for (int i = 0; i < signupActiveProfiles.length(); i++) {
					String propertySection = signupActiveProfiles.getString(i).equals(UiConstants.DEFAULT_STRING)
							? UiConstants.MOSIP_CONFIG_APPLICATION_HYPHEN_STRING + signupActiveProfiles.getString(i)
									+ UiConstants.DOT_PROPERTIES_STRING
							: signupActiveProfiles.getString(i) + UiConstants.DOT_PROPERTIES_STRING;

					value = getValueFromSignupActuatorWithUrl(propertySection, key, UiConstants.SIGNUP_ACTUATOR_URL);

					if (value != null && !value.isBlank()) {
						break;
					}
				}
			} else {
				logger.warn("No active profiles were retrieved.");
			}
		}

		// Fallback to a default section if no value found
		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(EsignetConfigManager.getEsignetActuatorPropertySection(), key,
					UiConstants.SIGNUP_ACTUATOR_URL);
		}

		// Final fallback to the original section if no value was found
		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(section, key, UiConstants.SIGNUP_ACTUATOR_URL);
		}

		// Log the final result or an error message if not found
		if (value == null || value.isBlank()) {
			logger.error("Value not found for section: " + section + ", key: " + key);
		}

		return value;
	}

	public static String getValueFromSignupActuatorWithUrl(String section, String key, String url) {
		// Generate cache key based on the url, section, and key
		String actuatorCacheKey = url + section + key;
		String value = actuatorValueCache.get(actuatorCacheKey);

		if (value != null && !value.isEmpty()) {
			return value; // Return cached value if available
		}

		try {
			// Fetch the actuator response array if not already populated
			if (signupActuatorResponseArray == null) {
				Response response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
				JSONObject responseJson = new JSONObject(response.getBody().asString());
				signupActuatorResponseArray = responseJson.getJSONArray("propertySources");
			}

			// Search through the property sources for the section
			for (int i = 0, size = signupActuatorResponseArray.length(); i < size; i++) {
				JSONObject eachJson = signupActuatorResponseArray.getJSONObject(i);
				if (eachJson.get("name").toString().contains(section)) {
					logger.info("Found properties: " + eachJson.getJSONObject(GlobalConstants.PROPERTIES));
					value = eachJson.getJSONObject(GlobalConstants.PROPERTIES).getJSONObject(key)
							.get(GlobalConstants.VALUE).toString();
					if (EsignetConfigManager.IsDebugEnabled()) {
						logger.info("Actuator: " + url + " key: " + key + " value: " + value);
					}
					break;
				}
			}

			// Cache the retrieved value
			if (value != null && !value.isEmpty()) {
				actuatorValueCache.put(actuatorCacheKey, value);
			}

			return value;
		} catch (JSONException e) {
			logger.error("Error parsing JSON for section: " + section + ", key: " + key + " - " + e.getMessage());
			return null;
		} catch (Exception e) {
			logger.error("Error fetching value for section: " + section + ", key: " + key + " - " + e.getMessage());
			return null;
		}
	}

	public static JSONArray getActiveProfilesFromActuator(String url, String key) {
		JSONArray activeProfiles = null;

		try {
			Response response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
			JSONObject responseJson = new JSONObject(response.getBody().asString());

			// If the key exists in the response, return the associated JSONArray
			if (responseJson.has(key)) {
				activeProfiles = responseJson.getJSONArray(key);
			} else {
				logger.warn("The key '" + key + "' was not found in the response.");
			}

		} catch (Exception e) {
			// Handle other errors like network issues, etc.
			logger.error("Error fetching active profiles from the actuator: " + e.getMessage());
		}

		return activeProfiles;
	}

	public static int getOtpResendDelayFromSignupActuator() {
		String value = getValueFromSignupActuator("classpath:/application-default.properties",
				"mosip.signup.challenge.resend-delay");
		if (value != null && !value.isBlank()) {
			return Integer.parseInt(value.trim());
		} else {
			logger.error("OTP resend delay value not found in actuator, using default 60s");
			return 60;
		}
	}

}
