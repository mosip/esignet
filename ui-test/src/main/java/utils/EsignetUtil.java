package utils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v134.network.Network;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.testng.SkipException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import constants.ESignetConstants;
import constants.UiConstants;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.GlobalMethods;
import io.mosip.testrig.apirig.utils.JWKKeyUtil;
import io.mosip.testrig.apirig.utils.RestClient;
import io.mosip.testrig.apirig.utils.SecurityXSSException;
import io.restassured.response.Response;
import runners.Runner;

public class EsignetUtil extends AdminTestUtil {

	private static final Logger logger = Logger.getLogger(EsignetUtil.class);
	public static String pluginName = null;
	public static JSONArray signupActiveProfiles = null;

	private static final String TOKEN_URL = EsignetConfigManager.getproperty("keycloak-external-url")
			+ EsignetConfigManager.getproperty("keycloakAuthTokenEndPoint");
	private static final String GRANT_TYPE = "client_credentials";
	private static final String CLIENT_ID = "client_id";
	private static final String CLIENT_SECRET = "client_secret";
	private static final String GRANT_TYPE_KEY = "grant_type";
	private static final String ACCESS_TOKEN = "access_token";

	private static String partnerCookie = null;
	private static String mobileAuthCookie = null;
	protected static boolean triggerESignetKeyGenForPAR = true;
	protected static final String OIDC_JWK_FOR_PAR = "oidcJWKForPAR";
	protected static RSAKey oidc_JWK_Key_For_PAR = null;

	private static final String display = "popup";
	private static final String responseType = "code";
	private static final String client_assertion_type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
	private static final String claim_locales = "en";
	private static final String scope = "openid profile";
	private static final String state = "eree2311";
	private static final String prompt = "login";
	private static final String aud_key = "pushed_authorization_request_endpoint";

	private static Response sendPostRequest(String url, Map<String, String> params) {
		try {
			return RestClient.postRequestWithFormDataBody(url, params);
		} catch (Exception e) {
			logger.error("Error sending POST request to URL: " + url, e);
			return null;
		}
	}

	private static org.openqa.selenium.devtools.v134.network.model.Response lastResponse;
	private static DevTools devTools;
	private static WebDriver driver;

	public static void setDriver(WebDriver webDriver) {
		driver = webDriver;
	}

	// Initialize ChromeDriver with Network capture
	public static WebDriver startDriverWithNetwork() {
		driver = new ChromeDriver();
		devTools = ((ChromeDriver) driver).getDevTools();
		devTools.createSession();
		devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));

		devTools.addListener(Network.responseReceived(), response -> {
			lastResponse = response.getResponse();
		});

		return driver;
	}

	// Get last response status
	public static int getLastStatusCode() {
		return (lastResponse != null) ? lastResponse.getStatus() : -1;
	}

	// Check if a network request was made to a given endpoint
	public static boolean verifyRequestMade(String endpointPath) {
		return (lastResponse != null && lastResponse.getUrl().contains(endpointPath));
	}

	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	public static String getPluginName() {
		if (pluginName != null)
			return pluginName;
		pluginName = EsignetConfigManager.getproperty("pluginToExecute");
		return pluginName;
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

	public static String generateMobileNumberFromRegex() {
		String regex = getValueFromSignupActuator("applicationConfig: [classpath:/application-default.properties]",
				"mosip.signup.identifier.regex");

		if (regex == null || regex.isEmpty()) {
			logger.error("Mobile number regex not found in configuration");
			throw new IllegalStateException("mosip.signup.identifier.regex is not configured");
		}

		int startIdx = regex.indexOf('{');
		int endIdx = regex.indexOf('}');
		if (startIdx == -1 || endIdx == -1 || startIdx >= endIdx) {
			logger.error("Invalid regex format: " + regex);
			throw new IllegalArgumentException("Regex must contain {min,max} pattern");
		}

		String digitRange = regex.substring(startIdx + 1, endIdx);
		String[] parts = digitRange.split(",");
		if (parts.length != 2) {
			throw new IllegalArgumentException("Regex digit range must be in format {min,max}");
		}

		int min = Integer.parseInt(parts[0].trim());
		int max = Integer.parseInt(parts[1].trim());
		int length = min + new Random().nextInt(max - min + 1);

		StringBuilder number = new StringBuilder();
		number.append(new Random().nextInt(9) + 1);

		for (int i = 1; i < length; i++) {
			number.append(new Random().nextInt(10));
		}

		return number.toString();
	}

	public static String getPasswordPattern() {
		return getValueFromSignupActuator("applicationConfig: [classpath:/application-default.properties]",
				"mosip.signup.password.pattern");
	}

	public static int getPasswordMinLength() {
		String value = getValueFromSignupActuator("applicationConfig: [classpath:/application-default.properties]",
				"mosip.signup.password.min-length");
		return Integer.parseInt(value);
	}

	public static int getPasswordMaxLength() {
		String value = getValueFromSignupActuator("applicationConfig: [classpath:/application-default.properties]",
				"mosip.signup.password.max-length");
		return Integer.parseInt(value);
	}

	public static String generateValidPasswordFromActuator() {
		int min = getPasswordMinLength();
		int max = getPasswordMaxLength();
		int length = min + new Random().nextInt(max - min + 1);

		String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		String lower = "abcdefghijklmnopqrstuvwxyz";
		String digits = "0123456789";
		String special = "_!@#$%^&*";
		String all = upper + lower + digits + special;

		StringBuilder password = new StringBuilder();

		password.append(upper.charAt(new Random().nextInt(upper.length())));
		password.append(lower.charAt(new Random().nextInt(lower.length())));
		password.append(digits.charAt(new Random().nextInt(digits.length())));
		password.append(special.charAt(new Random().nextInt(special.length())));

		for (int i = 4; i < length; i++) {
			password.append(all.charAt(new Random().nextInt(all.length())));
		}

		return password.toString();
	}

	private static JSONObject signupUISpecResponse;

	private static JSONObject getSignupUISpecResponse() {
		if (signupUISpecResponse == null) {
			try {
				logger.info("Loading Signup UI Spec from " + UiConstants.SIGNUP_UI_SPEC_URL);
				Response response = RestClient.getRequest(UiConstants.SIGNUP_UI_SPEC_URL, MediaType.APPLICATION_JSON,
						MediaType.APPLICATION_JSON);
				signupUISpecResponse = new JSONObject(response.getBody().asString());
			} catch (Exception e) {
				logger.error("Failed to load Signup UI Spec from URL.", e);
				signupUISpecResponse = new JSONObject();
			}
		}
		return signupUISpecResponse;
	}

	public static String getFieldProperty(String fieldId, String property, String langCode) {
		try {
			JSONArray schema = getSignupUISpecResponse().optJSONObject("response").optJSONArray("schema");

			if (schema == null) {
				logger.warn("Schema missing in UI Spec");
				return null;
			}

			for (int i = 0; i < schema.length(); i++) {
				JSONObject field = schema.getJSONObject(i);
				if (fieldId.equals(field.optString("id"))) {

					if (field.has(property) && field.opt(property) instanceof JSONObject) {
						JSONObject obj = field.optJSONObject(property);
						if (obj != null) {
							String value = obj.optString(langCode, null);
							logger.info(property + " for " + fieldId + " in " + langCode + ": " + value);
							return value;
						}
					}

					if ("validators".equals(property)) {
						JSONArray validators = field.optJSONArray("validators");
						if (validators == null)
							continue;

						for (int j = 0; j < validators.length(); j++) {
							JSONObject validator = validators.getJSONObject(j);
							if (langCode.equals(validator.optString("langCode"))) {
								String regex = validator.optString("regex", null);
								logger.info("Regex for " + fieldId + " in " + langCode + ": " + regex);
								return regex;
							}
						}
					}
				}
			}

			logger.warn("No " + property + " for " + fieldId + " in " + langCode);
		} catch (Exception e) {
			logger.error("Error getting " + property + " for " + fieldId + " - " + langCode, e);
		}
		return null;
	}

	public static String getRegexForField(String fieldId, String langCode) {
		return getFieldProperty(fieldId, "validators", langCode);
	}

	public static String getRegexForFullName(String langCode) {
		return getRegexForField("fullName", langCode);
	}

	public static class FullName {
		public String english;
		public String khmer;
	}

	public static FullName generateNamesFromUiSpec() {
		String enRegex = getRegexForFullName("en");
		String kmRegex = getRegexForFullName("km");

		int enMax = extractMaxLength(enRegex);
		int kmMax = extractMaxLength(kmRegex);

		FullName fullName = new FullName();
		fullName.english = generateEnglishName(enMax);
		fullName.khmer = generateKhmerName(kmMax);

		return fullName;
	}

	private static int extractLength(String regex, boolean isMax) {
		if (regex == null)
			return isMax ? 10 : 2;
		int start = regex.indexOf('{');
		int end = regex.indexOf('}');
		if (start != -1 && end != -1) {
			String[] parts = regex.substring(start + 1, end).split(",");
			if (parts.length == 2)
				return Integer.parseInt(parts[isMax ? 1 : 0].trim());
			return Integer.parseInt(parts[0].trim());
		}
		return isMax ? 10 : 2;
	}

	public static int extractMaxLength(String regex) {
		return extractLength(regex, true);
	}

	public static int extractMinLength(String regex) {
		return extractLength(regex, false);
	}

	public static String generateEnglishName(int maxLength) {
		String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ";
		Random random = new Random();
		int length = 2 + random.nextInt(Math.max(1, maxLength - 1));
		StringBuilder name = new StringBuilder();
		name.append((char) ('A' + random.nextInt(26)));
		for (int i = 1; i < length; i++) {
			name.append(letters.charAt(random.nextInt(letters.length())));
		}
		return name.toString().trim();
	}

	public static String generateKhmerName(int maxLength) {
		Random random = new Random();
		int length = 2 + random.nextInt(Math.max(1, maxLength - 1));
		StringBuilder name = new StringBuilder();

		int[][] ranges = { { 0x1780, 0x17FF }, { 0x19E0, 0x19FF }, };

		for (int i = 0; i < length; i++) {
			int[] range = ranges[random.nextInt(ranges.length)];
			int codePoint = range[0] + random.nextInt(range[1] - range[0] + 1);

			name.append((char) codePoint);
		}
		return name.toString();
	}

	public static class RegisteredDetails {

		private static String registeredMobileNumber;

		public static String getMobileNumber() {
			return registeredMobileNumber;
		}

		public static void setMobileNumber(String mobileNumber) {
			registeredMobileNumber = mobileNumber;
		}
	}

	private static boolean getTriggerESignetKeyGenForPAR() {
		return triggerESignetKeyGenForPAR;
	}

	private static void setTriggerESignetKeyGenForPAR(boolean value) {
		triggerESignetKeyGenForPAR = value;
	}

	public static void getSupportedLanguage() {

		if (EsignetConfigManager.getproperty("esignetSupportedLanguage") != null) {
			BaseTestCase.languageList
					.add(EsignetConfigManager.getproperty(ESignetConstants.ESIGNET_SUPPORTED_LANGUAGE));
			logger.info("Supported Language = "
					+ EsignetConfigManager.getproperty(ESignetConstants.ESIGNET_SUPPORTED_LANGUAGE));
		} else {
			logger.error("Language not found");
		}
	}

	public static String inputstringKeyWordHandler(String jsonString, String testCaseName) {
		if (jsonString.contains("$ID:")) {
			jsonString = replaceIdWithAutogeneratedId(jsonString, "$ID:");
		}

		if (jsonString.contains(GlobalConstants.TIMESTAMP)) {
			jsonString = replaceKeywordWithValue(jsonString, GlobalConstants.TIMESTAMP, generateCurrentUTCTimeStamp());
		}

		if (jsonString.contains("$UNIQUENONCEVALUEFORESIGNET$")) {
			jsonString = replaceKeywordWithValue(jsonString, "$UNIQUENONCEVALUEFORESIGNET$",
					String.valueOf(Calendar.getInstance().getTimeInMillis()));
		}

		if (jsonString.contains("$CLIENT_ASSERTION_PAR_JWT$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDC_JWK_FOR_PAR);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidc_JWK_Key_For_PAR = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidc_JWK_Key_For_PAR =" + oidc_JWK_Key_For_PAR);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}

			JSONObject root = new JSONObject(jsonString);
			String clientId = root.optString("client_id", null);
			String audKey = null;

			if (root.has("aud_key")) {
				audKey = root.optString("aud_key", null);
				root.remove("aud_key");
				jsonString = root.toString();
			}

			String tempUrl = getValueFromEsignetWellKnownEndPoint(audKey, EsignetConfigManager.getEsignetBaseUrl());

			if (clientId != null) {
				jsonString = replaceKeywordWithValue(jsonString, "$CLIENT_ASSERTION_PAR_JWT$",
						signJWKKey(clientId, oidc_JWK_Key_For_PAR, tempUrl));
			} else {
				logger.error("Client ID not found in JSON for $CLIENT_ASSERTION_PAR_JWT$.");
			}
		}

		if (jsonString.contains("$OIDC_JWK_KEY_PAR$")) {
			String jwkKey = "";
			if (getTriggerESignetKeyGenForPAR()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDC_JWK_FOR_PAR);
				setTriggerESignetKeyGenForPAR(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDC_JWK_FOR_PAR);
			}
			jsonString = replaceKeywordWithValue(jsonString, "$OIDC_JWK_KEY_PAR$", jwkKey);
		}

		if (jsonString.contains("$ESIGNET_REDIRECT_URI$")) {
			jsonString = replaceKeywordWithValue(jsonString, "$ESIGNET_REDIRECT_URI$",
					EsignetConfigManager.getproperty("baseurl") + "userprofile");
		}

		return jsonString;

	}

	public static String getValueFromEsignetWellKnownEndPoint(String key, String baseURL) {
		String url = baseURL + EsignetConfigManager.getproperty("esignetWellKnownEndPoint");
		Response response = null;
		try {
			response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
			JSONObject responseJson = new JSONObject(response.getBody().asString());
			return responseJson.getString(key);
		} catch (Exception e) {
			logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			return null;
		}
	}

	public static String signJWKKey(String clientId, RSAKey jwkKey, String tempUrl) {
		int idTokenExpirySecs = Integer
				.parseInt(getValueFromEsignetActuator(EsignetConfigManager.getEsignetActuatorPropertySection(),
						GlobalConstants.MOSIP_ESIGNET_ID_TOKEN_EXPIRE_SECONDS));
		JWSSigner signer;

		try {
			signer = new RSASSASigner(jwkKey);

			Date currentTime = new Date();

			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentTime);

			calendar.add(Calendar.HOUR_OF_DAY, (idTokenExpirySecs / 3600));

			Date expirationTime = calendar.getTime();

			JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject(clientId).audience(tempUrl).issuer(clientId)
					.issueTime(currentTime).expirationTime(expirationTime).jwtID(UUID.randomUUID().toString()).build();

			logger.info("JWT current and expiry time " + currentTime + " & " + expirationTime);

			SignedJWT signedJWT = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwkKey.getKeyID()).build(), claimsSet);

			signedJWT.sign(signer);
			clientAssertionToken = signedJWT.serialize();
		} catch (Exception e) {
			logger.error("Exception while signing oidcJWKKey for client assertion: " + e.getMessage());
		}
		return clientAssertionToken;
	}

	public static JSONObject getOauthDetailsBody() {
		LogEntries logs = driver.manage().logs().get("performance");

		for (LogEntry log : logs) {
			try {
				JSONObject msg = new JSONObject(log.getMessage());
				JSONObject request = msg.getJSONObject("message").getJSONObject("params").optJSONObject("request");

				if (request == null)
					continue;

				String url = request.optString("url", "");
				if (!url.contains("oauth-details"))
					continue;

				String postData = request.optString("postData", "");
				if (!postData.isEmpty()) {
					return new JSONObject(postData);
				}

			} catch (Exception ignored) {
			}
		}
		return null;
	}

	public static String isTestCaseValidForExecution(TestCaseDTO testCaseDTO) {
		String testCaseName = testCaseDTO.getTestCaseName();

		int indexof = testCaseName.indexOf("_");
		String modifiedTestCaseName = testCaseName.substring(indexof + 1);

		addTestCaseDetailsToMap(modifiedTestCaseName, testCaseDTO.getUniqueIdentifier());

		if (isCaptchaEnabled() == true) {
			GlobalMethods.reportCaptchaStatus(GlobalConstants.CAPTCHA_ENABLED, true);
			throw new SkipException(GlobalConstants.CAPTCHA_ENABLED_MESSAGE);
		}

		if (Runner.skipAll == true) {
			throw new SkipException(GlobalConstants.PRE_REQUISITE_FAILED_MESSAGE);
		}

		if (pluginName.equals("mock")) {
			BaseTestCase.setSupportedIdTypes(Arrays.asList("UIN"));

			String endpoint = testCaseDTO.getEndPoint();
			if (endpoint.contains("/esignet/") == false && endpoint.contains("/mock-identity-system/") == false) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

		} else if (pluginName.equals("mosipid")) {
			getSupportedIdTypesValueFromActuator();

			logger.info("supportedIdType = " + supportedIdType);

			String endpoint = testCaseDTO.getEndPoint();
			if (endpoint.contains("/mock-identity-system/") == true
					|| ((testCaseName.equals("ESignetUI_CreateOIDCClient_all_Valid_Smoke_sid"))
							&& endpoint.contains("/v1/esignet/client-mgmt/client"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}
		}
		return testCaseName;
	}

	public static String getAuthTokenFromKeyCloak(String clientId, String clientSecret) {
		Map<String, String> params = new HashMap<>();
		params.put(CLIENT_ID, clientId);
		params.put(CLIENT_SECRET, clientSecret);
		params.put(GRANT_TYPE_KEY, GRANT_TYPE);

		Response response = sendPostRequest(TOKEN_URL, params);

		if (response == null) {
			return "";
		}
		logger.info(response.getBody().asString());

		JSONObject responseJson = new JSONObject(response.getBody().asString());
		return responseJson.optString(ACCESS_TOKEN, "");
	}

	public static String getAuthTokenByRole(String role) {
		if (role == null)
			return "";

		String roleLowerCase = role.toLowerCase();
		switch (roleLowerCase) {
		case "partner":
			if (!AdminTestUtil.isValidToken(partnerCookie)) {
				partnerCookie = getAuthTokenFromKeyCloak(EsignetConfigManager.getPmsClientId(),
						EsignetConfigManager.getPmsClientSecret());
			}
			return partnerCookie;
		case "mobileauth":
			if (!AdminTestUtil.isValidToken(mobileAuthCookie)) {
				mobileAuthCookie = getAuthTokenFromKeyCloak(EsignetConfigManager.getMPartnerMobileClientId(),
						EsignetConfigManager.getMPartnerMobileClientSecret());
			}
			return mobileAuthCookie;
		default:
			return "";
		}
	}

	public static Response postWithBodyAndBearerToken(String url, String jsonInput, String cookieName, String role,
			String testCaseName, String idKeyName) {
		Response response = null;
		if (testCaseName.contains("Invalid_Token")) {
			token = "xyz";
		} else if (testCaseName.contains("NOAUTH")) {
			token = "";
		} else {
			token = getAuthTokenByRole(role);
		}
		logger.info(GlobalConstants.POST_REQ_URL + url);
		GlobalMethods.reportRequest(null, jsonInput, url);
		try {
			response = RestClient.postRequestWithBearerToken(url, jsonInput, MediaType.APPLICATION_JSON,
					MediaType.APPLICATION_JSON, cookieName, token);
			GlobalMethods.reportResponse(response.getHeaders().asList().toString(), url, response);

			return response;
		} catch (Exception e) {
			logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			return response;
		}
	}

	protected static Response postWithBodyAndCookieForAutoGeneratedIdForUrlEncoded(String url, String jsonInput)
			throws SecurityXSSException {
		Response response = null;
		jsonInput = inputstringKeyWordHandler(jsonInput, "");
		ObjectMapper mapper = new ObjectMapper();
		Map<String, String> map = null;
		try {
			map = mapper.readValue(jsonInput, Map.class);
			logger.info(GlobalConstants.POST_REQ_URL + url);
			logger.info(jsonInput);
			GlobalMethods.reportRequest(null, jsonInput, url);
			response = RestClient.postRequestWithFormDataBody(url, map);
			GlobalMethods.checkXSSProtectionHeader(response, url);
			GlobalMethods.reportResponse(response.getHeaders().asList().toString(), url, response);

			return response;
		} catch (SecurityXSSException se) {
			String responseHeadersString = (response == null) ? "No response"
					: response.getHeaders().asList().toString();
			String errorMessageString = "XSS check failed for URL: " + url + "\nHeaders: " + responseHeadersString
					+ "\nError: " + se.getMessage();
			logger.error(errorMessageString, se);
			throw se;
		} catch (Exception e) {
			logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			return response;
		}
	}

	public static String generateParRequestUri() throws SecurityXSSException, JsonProcessingException {

		String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		String parUrl = baseUrl + "/v1/esignet/oauth/par";

		JSONObject requestBody = new JSONObject();

		requestBody.put("display", display);
		requestBody.put("response_type", responseType);
		requestBody.put("nonce", "$UNIQUENONCEVALUEFORESIGNET$");
		requestBody.put("client_id", AdminTestUtil
				.replaceIdWithAutogeneratedId("$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$", "$ID:"));
		requestBody.put("requestTime", "$TIMESTAMP$");
		requestBody.put("client_assertion_type", client_assertion_type);
		requestBody.put("claim_locales", claim_locales);
		requestBody.put("scope", scope);
		requestBody.put("acr_values",
				"mosip:idp:acr:generated-code mosip:idp:acr:biometrics mosip:idp:acr:linked-wallet mosip:idp:acr:password");
		requestBody.put("redirect_uri", "$ESIGNET_REDIRECT_URI$");
		requestBody.put("state", state);
		requestBody.put("client_assertion", "$CLIENT_ASSERTION_PAR_JWT$");
		requestBody.put("prompt", prompt);
		requestBody.put("aud_key", aud_key);

		Response response = postWithBodyAndCookieForAutoGeneratedIdForUrlEncoded(parUrl, requestBody.toString());

		JSONObject responseJson = new JSONObject(response.asString());
		return responseJson.getString("request_uri");
	}

}
