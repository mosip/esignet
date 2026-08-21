package utils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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

	private static final String[] SUNBIRD_R_FIRST_NAMES = { "Alex", "Jordan", "Taylor", "Morgan", "Casey", "Riley",
			"Sam", "Jamie" };
	private static final String[] SUNBIRD_R_LAST_NAMES = { "Smith", "Brown", "Johnson", "Lee", "Clark", "Walker",
			"Young", "Hill" };
	private static final String sunBirdRFullName = SUNBIRD_R_FIRST_NAMES[new Random().nextInt(SUNBIRD_R_FIRST_NAMES.length)]
			+ " " + SUNBIRD_R_LAST_NAMES[new Random().nextInt(SUNBIRD_R_LAST_NAMES.length)];
	private static final String sunBirdRDob = LocalDate.ofEpochDay(LocalDate.of(1970, 1, 1).toEpochDay()
			+ (long) (Math.random() * (LocalDate.of(2000, 12, 31).toEpochDay() - LocalDate.of(1970, 1, 1).toEpochDay())))
			.format(DateTimeFormatter.ISO_LOCAL_DATE);
	private static final String sunBirdRPolicyNumber = String.valueOf(100000000 + new Random().nextInt(900000000));

	public static String getSunBirdRFullName() {
		return sunBirdRFullName;
	}

	public static String getSunBirdRDob() {
		return sunBirdRDob;
	}

	public static String getSunBirdRPolicyNumber() {
		return sunBirdRPolicyNumber;
	}

	private static final String SUNBIRD_R_MOBILE = "0123456789";
	private static final String SUNBIRD_R_GENDER = "Male";
	private static final String SUNBIRD_R_EMAIL = "esignetui.sunbird@example.com";
	private static final String SUNBIRD_R_POLICY_NAME = "Start Insurance Gold Premium";

	public static String getSunBirdRPolicyName() {
		return SUNBIRD_R_POLICY_NAME;
	}

	public static String getSunBirdRMobile() {
		return SUNBIRD_R_MOBILE;
	}

	public static String getSunBirdRGender() {
		return SUNBIRD_R_GENDER;
	}

	public static String getSunBirdREmail() {
		return SUNBIRD_R_EMAIL;
	}

	/** Which KBI schema field id is the identity lookup key (maps to the Sunbird policyNumber). */
	public static String getKbiIndividualIdField() {
		JSONObject configs = ClaimsUtil.getConfigs();
		return configs != null ? configs.optString("auth.factor.kbi.individual-id-field", null) : null;
	}

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
	protected static boolean triggerESignetKeyGenForPARPurposeLogin = true;
	protected static boolean triggerESignetKeyGenForPARPurposeLink = true;
	protected static boolean triggerESignetKeyGenForPARPurposeNone = true;
	protected static boolean triggerESignetKeyGenForPARNoPurpose = true;
	protected static boolean triggerESignetKeyGenForPARNoTitle = true;
	protected static boolean triggerESignetKeyGenForPAREmptyTitle = true;
	protected static boolean triggerESignetKeyGenForPARSingleAcrValue = true;
	protected static final String OIDC_JWK_FOR_PAR = "oidcJWKForPAR";
	protected static final String OIDC_JWK_FOR_PAR_PURPOSE_LOGIN = "oidcJWKForPARPurposeLogin";
	protected static final String OIDC_JWK_FOR_PAR_PURPOSE_LINK = "oidcJWKForPARPurposeLink";
	protected static final String OIDC_JWK_FOR_PAR_PURPOSE_VERIFY = "oidcJWKForPARPurposeVerify";
	protected static final String OIDC_JWK_FOR_PAR_PURPOSE_NONE = "oidcJWKForPARPurposeNone";
	protected static final String OIDC_JWK_FOR_PAR_NO_PURPOSE = "oidcJWKForPARNoPurposeType";
	protected static final String OIDC_JWK_FOR_PAR_NO_TITLE = "oidcJWKForPARNoTitle";
	protected static final String OIDC_JWK_FOR_PAR_EMPTY_TITLE = "oidcJWKForPAREmptyTitle";
	protected static final String OIDC_JWK_FOR_PAR_SINGLE_ACR_VALUE = "oidcJWKForPARSingleAcrValue";
	protected static final String OIDC_JWK_FOR_PAR_REQUIRED = "oidcJWKForParRequired";
	protected static RSAKey oidc_JWK_Key_For_PAR = null;
	protected static final String CLAIMS_REQUEST = "config/claims.json";

	private static final String display = "popup";
	private static final String responseType = "code";
	private static final String client_assertion_type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
	private static final String claim_locales = "en";
	private static final String scope = "openid profile";
	public static final String AUTHORIZE_SCOPE_ONLY = "openid Manage-VID";
	private static final String state = "eree2311";
	// Used on every authorize URL, including LoginOptions.feature's re-login scenario that asserts
	// consent is skipped on repeat login - prompt=consent forces a fresh consent screen every time,
	// which would invalidate that assertion. Not changed here: this constant feeds every scenario's
	// authorize URL, so switching it risks regressing the (many) scenarios that rely on prompt=consent
	// actually showing consent. That specific assertion is currently a no-op outside the "mosipid"
	// plugin (see ConsentStepDefinition#prerequisiteVidsAvailableForConsentRegistry), so this is inert
	// under the mock-plugin config this module runs against.
	private static final String prompt = "consent";
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

	public static int getLastStatusCode() {
		return (lastResponse != null) ? lastResponse.getStatus() : -1;
	}

	public static boolean verifyRequestMade(String endpointPath) {
		return (lastResponse != null && lastResponse.getUrl().contains(endpointPath));
	}

	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	public static boolean isMockPlugin() {
		return "mock".equalsIgnoreCase(getPluginName());
	}

	/** Shared by step-definition classes whose feature doesn't exist under the mock-plugin flow. */
	public static boolean notApplicableUnderMockPlugin(String featureDescription, Logger callerLogger) {
		if (isMockPlugin()) {
			String reason = featureDescription
					+ " does not exist under this environment's mock-plugin flow - verified live.";
			callerLogger.info("Not checking (this step only, not the scenario) - " + reason);
			ExtentReportManager.notApplicable(reason);
			return true;
		}
		return false;
	}

	public static String getPluginName() {
		if (pluginName != null)
			return pluginName;

		String configuredPlugin = EsignetConfigManager.getProperty("pluginToExecute", "").trim().toLowerCase();
		if (configuredPlugin.equals("mosipid") || configuredPlugin.equals("mock")) {
			pluginName = configuredPlugin;
			return pluginName;
		}
		if (!configuredPlugin.isBlank()) {
			logger.warn("Ignoring pluginToExecute='" + configuredPlugin
					+ "' - expected 'mosipid' or 'mock'; falling back to actuator auto-detection");
		}

		String serverAuthenticator = getIdentityPluginNameFromEsignetActuator();
		if (serverAuthenticator == null || serverAuthenticator.isBlank()) {
			logger.error("Could not read mosip.esignet.integration.authenticator from the eSignet actuator - "
					+ "assuming 'mosipid' for this call without caching it");
			return "mosipid";
		}
		boolean isMockLike = serverAuthenticator.toLowerCase().contains("mockauthenticationservice")
				|| serverAuthenticator.toLowerCase().contains("sunbirdrcauthenticationservice");
		pluginName = isMockLike ? "mock" : "mosipid";
		return pluginName;
	}

	private static Boolean captchaEnabled = null;

	public static boolean isCaptchaEnabled() {
		if (captchaEnabled != null) {
			return captchaEnabled;
		}

		String configuredValue = EsignetConfigManager.getProperty("captchaEnabled", "").trim();
		if (!configuredValue.isEmpty()) {
			captchaEnabled = parseConfiguredBoolean("captchaEnabled", false);
			return captchaEnabled;
		}

		if (!isEsignetActuatorEnabled()) {
			captchaEnabled = false;
			return captchaEnabled;
		}

		captchaEnabled = AdminTestUtil.isCaptchaEnabled();
		return captchaEnabled;
	}

	public static JSONArray signupActuatorResponseArray = null;

	public static String getValueFromSignupActuator(String section, String key) {

		String value = null;
		String keyForEnvVariableSection = key.toUpperCase().replace("-", "_").replace(".", "_");

		if (signupActiveProfiles == null || signupActiveProfiles.length() == 0) {
			signupActiveProfiles = getActiveProfilesFromActuator(UiConstants.SIGNUP_ACTUATOR_URL,
					UiConstants.ACTIVE_PROFILES);
		}

		value = getValueFromSignupActuatorWithUrl(UiConstants.SYSTEM_ENV_SECTION, keyForEnvVariableSection,
				UiConstants.SIGNUP_ACTUATOR_URL);

		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(UiConstants.CLASS_PATH_APPLICATION_PROPERTIES, key,
					UiConstants.SIGNUP_ACTUATOR_URL);
		}

		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(UiConstants.CLASS_PATH_APPLICATION_DEFAULT_PROPERTIES, key,
					UiConstants.SIGNUP_ACTUATOR_URL);
		}

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

		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(EsignetConfigManager.getEsignetActuatorPropertySection(), key,
					UiConstants.SIGNUP_ACTUATOR_URL);
		}

		if (value == null || value.isBlank()) {
			value = getValueFromSignupActuatorWithUrl(section, key, UiConstants.SIGNUP_ACTUATOR_URL);
		}

		if (value == null || value.isBlank()) {
			logger.error("Value not found for section: " + section + ", key: " + key);
		}

		return value;
	}

	public static String getValueFromSignupActuatorWithUrl(String section, String key, String url) {
		String actuatorCacheKey = url + section + key;
		String value = actuatorValueCache.get(actuatorCacheKey);

		if (value != null && !value.isEmpty()) {
			return value;
		}

		try {
			if (signupActuatorResponseArray == null) {
				Response response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
				JSONObject responseJson = new JSONObject(response.getBody().asString());
				signupActuatorResponseArray = responseJson.getJSONArray("propertySources");
			}

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

	private static Boolean signupServiceDeployed = null;

	/**
	 * Cached reachability probe for the signup service actuator. The end-to-end registration
	 * scenario (and every scenario that reuses its phone number) needs this to fail fast with a
	 * skip rather than an NPE/timeout when signup isn't deployed in the environment.
	 *
	 * Confirmed live for this environment two independent ways: this actuator call 404s (no
	 * signupUrl configured, and Thunder has no Spring actuator at all regardless), AND navigating a
	 * real browser to https://esignet-go.esqa.mosip.net/signup renders the SPA's own client-side
	 * "Page Not Found" page. Signup genuinely isn't deployed here, not just unreachable by this check.
	 */
	public static boolean isSignupServiceDeployed() {
		if (signupServiceDeployed == null) {
			try {
				Response response = RestClient.getRequest(UiConstants.SIGNUP_ACTUATOR_URL, MediaType.APPLICATION_JSON,
						MediaType.APPLICATION_JSON);
				signupServiceDeployed = response != null && response.getStatusCode() == 200;
			} catch (Exception e) {
				logger.warn("Signup service actuator unreachable at " + UiConstants.SIGNUP_ACTUATOR_URL
						+ " - treating signup service as not deployed: " + e.getMessage());
				signupServiceDeployed = false;
			}
		}
		return signupServiceDeployed;
	}

	public static JSONArray getActiveProfilesFromActuator(String url, String key) {
		JSONArray activeProfiles = null;

		try {
			Response response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
			JSONObject responseJson = new JSONObject(response.getBody().asString());

			if (responseJson.has(key)) {
				activeProfiles = responseJson.getJSONArray(key);
			} else {
				logger.warn("The key '" + key + "' was not found in the response.");
			}

		} catch (Exception e) {
			logger.error("Error fetching active profiles from the actuator: " + e.getMessage());
		}

		return activeProfiles;
	}

	public static String generateMobileNumberFromRegex() {
		String regex = getValueFromSignupActuator("applicationConfig: [classpath:/application-default.properties]",
				"mosip.signup.identifier.regex");

		String phoneNumber = "";
		try {
			phoneNumber = AdminTestUtil.genStringAsperRegex(regex);
		} catch (Exception e) {
			logger.info("Phone Number is not generated with regex: " + e);
		}

		return stripCountryCode(phoneNumber, regex);
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

						List<String> regexList = new ArrayList<>();

						for (int j = 0; j < validators.length(); j++) {
							JSONObject validator = validators.getJSONObject(j);

							if (validator.has("langCode")) {
								if (langCode.equalsIgnoreCase(validator.optString("langCode"))) {
									String regex = validator.optString("regex", null);
									if (regex != null && !regex.isEmpty()) {
										logger.info("Regex for " + fieldId + " in " + langCode + ": " + regex);
										return regex;
									}
								}
							} else {
								String regex = validator.optString("regex", null);
								if (regex != null && !regex.isEmpty()) {
									regexList.add(regex);
								}
							}
						}
						if (!regexList.isEmpty()) {
							StringBuilder combined = new StringBuilder();
							for (String r : regexList) {
								combined.append("(?=").append(r).append(")");
							}
							String combinedRegex = combined.append(".*").toString();
							logger.info("Combined Regex for " + fieldId + ": " + combinedRegex);
							return combinedRegex;
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

	/** KBI form schema for the current transaction (set via {@link ClaimsUtil#parseFromUrl(String)}). */
	public static JSONArray getKbiFieldSchema() {
		JSONObject configs = ClaimsUtil.getConfigs();
		if (configs == null) {
			return new JSONArray();
		}
		JSONObject kbi = configs.optJSONObject("auth.factor.kbi.field-details");
		if (kbi == null) {
			return new JSONArray();
		}
		JSONArray schema = kbi.optJSONArray("schema");
		return schema != null ? schema : new JSONArray();
	}

	/** Ordered list of field ids declared in the current transaction's KBI form schema. */
	public static List<String> getKbiFieldIds() {
		JSONArray schema = getKbiFieldSchema();
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < schema.length(); i++) {
			JSONObject field = schema.optJSONObject(i);
			String id = field != null ? field.optString("id", null) : null;
			if (id != null && !id.isBlank()) {
				ids.add(id);
			}
		}
		logger.info("KBI schema field ids: " + ids);
		return ids;
	}

	/** The schema-declared label for a KBI field in the given language code (e.g. "eng"), or null. */
	public static String getKbiFieldLabel(String fieldId, String langCode) {
		JSONObject field = findKbiField(fieldId);
		JSONObject labelName = field != null ? field.optJSONObject("labelName") : null;
		return labelName != null ? labelName.optString(langCode, null) : null;
	}

	/** Schema-declared option labels for a dropdown KBI field, keyed by subType (or id) and falling back to English. */
	public static List<String> getKbiDropdownOptionLabels(String fieldId, String langCode) {
		JSONObject configs = ClaimsUtil.getConfigs();
		JSONObject kbi = configs != null ? configs.optJSONObject("auth.factor.kbi.field-details") : null;
		JSONObject allowedValues = kbi != null ? kbi.optJSONObject("allowedValues") : null;
		JSONObject field = findKbiField(fieldId);
		if (allowedValues == null || field == null) {
			return new ArrayList<>();
		}
		JSONObject values = allowedValues.optJSONObject(field.optString("subType", fieldId));
		if (values == null) {
			values = allowedValues.optJSONObject(fieldId);
		}
		if (values == null) {
			return new ArrayList<>();
		}
		List<String> labels = new ArrayList<>();
		for (String key : values.keySet()) {
			JSONObject label = values.optJSONObject(key);
			if (label == null) {
				continue;
			}
			String text = label.optString(langCode, null);
			if (text == null || text.isBlank()) {
				text = label.optString("eng", null);
			}
			if (text != null && !text.isBlank()) {
				labels.add(text);
			}
		}
		return labels;
	}

	/** Whether the current transaction's schema marks a KBI field as required. */
	public static boolean isKbiFieldRequired(String fieldId) {
		JSONObject field = findKbiField(fieldId);
		return field != null && field.optBoolean("required", false);
	}

	/** Validators (regex + per-language error) declared for a KBI field, or empty. */
	public static JSONArray getKbiFieldValidators(String fieldId) {
		JSONObject field = findKbiField(fieldId);
		JSONArray validators = field != null ? field.optJSONArray("validators") : null;
		return validators != null ? validators : new JSONArray();
	}

	/** The schema's generic "this field is required" message, in the given language, or null. */
	public static String getKbiRequiredErrorMessage(String langCode) {
		JSONObject configs = ClaimsUtil.getConfigs();
		JSONObject kbi = configs != null ? configs.optJSONObject("auth.factor.kbi.field-details") : null;
		JSONObject errors = kbi != null ? kbi.optJSONObject("errors") : null;
		JSONObject required = errors != null ? errors.optJSONObject("required") : null;
		return required != null ? required.optString(langCode, null) : null;
	}

	private static JSONObject findKbiField(String fieldId) {
		JSONArray schema = getKbiFieldSchema();
		for (int i = 0; i < schema.length(); i++) {
			JSONObject field = schema.optJSONObject(i);
			if (field != null && fieldId.equals(field.optString("id"))) {
				return field;
			}
		}
		return null;
	}

	public static class FullName {
		public String english;
		public String khmer;
	}

	public static FullName generateNamesFromUiSpec() {
		String enRegex = getRegexForFullName("en");
		String kmRegex = getRegexForFullName("km");

		int enMin = extractMinLength(enRegex);
		int enMax = extractMaxLength(enRegex);
		int kmMin = extractMinLength(kmRegex);
		int kmMax = extractMaxLength(kmRegex);

		FullName fullName = new FullName();
		fullName.english = generateEnglishName(enMin, enMax);
		fullName.khmer = generateKhmerName(kmMin, kmMax);

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

	private static int targetNameLength(int minLength, int maxLength, Random random) {
		int min = Math.max(minLength, 2);
		int max = Math.max(min, maxLength);
		return min + random.nextInt(max - min + 1);
	}

	/**
	 * Letters only, plus single spaces between "words" - never a leading/trailing/doubled space,
	 * and always exactly targetLength characters, so trimming can never silently drop the result
	 * below the schema's minimum length (extractMinLength was previously computed but unused).
	 */
	public static String generateEnglishName(int minLength, int maxLength) {
		String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
		Random random = new Random();
		int targetLength = targetNameLength(minLength, maxLength, random);

		StringBuilder name = new StringBuilder();
		name.append((char) ('A' + random.nextInt(26)));
		while (name.length() < targetLength) {
			boolean canInsertSpace = name.length() < targetLength - 1 && name.charAt(name.length() - 1) != ' ';
			if (canInsertSpace && random.nextInt(6) == 0) {
				name.append(' ');
			} else {
				name.append(letters.charAt(random.nextInt(letters.length())));
			}
		}
		return name.toString();
	}

	/**
	 * Khmer independent consonants only (U+1780-U+17A2) - these are valid standalone base
	 * characters. Deliberately excludes: combining vowel signs/diacritics elsewhere in the Khmer
	 * block, which are only valid *following* a consonant and produce malformed sequences on their
	 * own; and the separate Khmer Symbols block (U+19E0-U+19FF), which is lunar calendar date
	 * glyphs, not name characters at all - the previous version drew from both, which is the likely
	 * cause of "invalid name" validation failures.
	 */
	public static String generateKhmerName(int minLength, int maxLength) {
		Random random = new Random();
		int targetLength = targetNameLength(minLength, maxLength, random);

		int consonantStart = 0x1780;
		int consonantEnd = 0x17A2;

		StringBuilder name = new StringBuilder();
		for (int i = 0; i < targetLength; i++) {
			int codePoint = consonantStart + random.nextInt(consonantEnd - consonantStart + 1);
			name.append((char) codePoint);
		}
		return name.toString();
	}

	public class RegisteredDetails {

		private static String registeredMobileNumber;
		private static String registeredFullName;
		private static String registeredPassword;

		public static String getMobileNumber() {
			return registeredMobileNumber;
		}

		public static void setMobileNumber(String mobileNumber) {
			registeredMobileNumber = mobileNumber;
		}

		public static String getPassword() {
			return registeredPassword;
		}

		public static void setPassword(String password) {
			registeredPassword = password;
		}

		public static String getFullName() {
			return registeredFullName;
		}

		public static void setFullName(String reisteredFullName) {
			registeredFullName = reisteredFullName;
		}
	}

	/**
	 * Login-phone source for every scenario except the end-to-end registration flow itself: reads
	 * the phone number the "Adding Identity" prerequisite generated (mosipid: AddIdentity.yml,
	 * mock: AddIdentityMock/AddIdentity.yml) instead of depending on the real signup UI having run.
	 * The cache key mirrors AdminTestUtil#getAutogenIdKeyName, which strips the testcase name down
	 * to everything after its first underscore before appending the field name.
	 */
	public static String getPrerequisiteRegisteredPhoneNumber() {
		String configuredPhoneNumber = EsignetConfigManager.getproperty("uinPhoneNumber");
		if (configuredPhoneNumber != null && !configuredPhoneNumber.isBlank()) {
			return configuredPhoneNumber.trim();
		}

		boolean isMock = "mock".equalsIgnoreCase(getPluginName());
		String cacheKey = isMock ? "AddIdentity_Valid_Parameters_smoke_Pos_UIN"
				: "AddIdentity_withValidParameters_smoke_Pos_PHONE";
		String phoneNumber = autoGeneratedIDValueCache.get(cacheKey);
		String schemaSource = isMock ? getMockIdentityFieldPattern("individualId") : phoneSchemaRegex;
		return stripCountryCode(phoneNumber, schemaSource);
	}

	public static final String PERPETUAL_VID_CACHE_KEY = "Generate_Perpetual_VID_Valid_Smoke_sid_vid";
	public static final String TEMPORARY_VID_CACHE_KEY = "Generate_Temporary_VID_Valid_Smoke_sid_vid";

	public static String getPrerequisitePerpetualVid() {
		return getPrerequisiteVidFromConfigOrCache(0, PERPETUAL_VID_CACHE_KEY);
	}

	public static String getPrerequisiteTemporaryVid() {
		return getPrerequisiteVidFromConfigOrCache(1, TEMPORARY_VID_CACHE_KEY);
	}

	public static boolean arePrerequisiteVidsAvailable() {
		String vid1 = getPrerequisitePerpetualVid();
		String vid2 = getPrerequisiteTemporaryVid();
		return vid1 != null && !vid1.isBlank() && vid2 != null && !vid2.isBlank();
	}

	private static String getPrerequisiteVidFromConfigOrCache(int configIndex, String cacheKey) {
		String configuredVids = EsignetConfigManager.getproperty("vid");
		if (configuredVids != null && !configuredVids.isBlank()) {
			String[] vidList = configuredVids.split(",");
			if (configIndex < vidList.length) {
				return vidList[configIndex].trim();
			}
			return null;
		}
		return autoGeneratedIDValueCache.get(cacheKey);
	}

	private static final Map<String, String> mockIdentityFieldPatternCache = new ConcurrentHashMap<>();

	private static String getMockIdentityFieldPattern(String fieldName) {
		return mockIdentityFieldPatternCache.computeIfAbsent(fieldName, field -> {
			try {
				String schemaStr = getMockIdentitySchema();
				Matcher matcher = Pattern
						.compile("\"" + field + "\"\\s*:\\s*\\{[^{}]*\"pattern\"\\s*:\\s*\"([^\"]+)\"",
								Pattern.CASE_INSENSITIVE)
						.matcher(schemaStr);
				String pattern = matcher.find() ? matcher.group(1) : "";
				if (pattern.isEmpty()) {
					logger.warn("Could not find a \"" + field
							+ "\" field with a \"pattern\" in the mock identity schema - country code stripping "
							+ "will be skipped for the mock-generated value");
				}
				return pattern;
			} catch (Exception e) {
				logger.warn("Failed to extract " + field + " pattern from mock identity schema: " + e.getMessage());
				return "";
			}
		});
	}

	private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("\\+\\]?(\\d+)");

	/**
	 * AddIdentity generates the phone number straight off the ID schema's validator regex (E.164,
	 * country code included) because that's what the identity-creation API requires. The esignet
	 * login field expects only the local number - the UI selects the country code separately - so
	 * whatever country code this deployment's schema actually uses has to be stripped here. Derived
	 * live from the same regex rather than a hardcoded country list, since that regex is the
	 * environment's actual source of truth and can differ per deployment.
	 */
	private static String stripCountryCode(String phoneNumber, String schemaRegex) {
		if (phoneNumber == null || phoneNumber.isBlank()) {
			return phoneNumber;
		}
		if (schemaRegex != null && !schemaRegex.isBlank()) {
			Matcher matcher = COUNTRY_CODE_PATTERN.matcher(schemaRegex);
			if (matcher.find()) {
				String countryCode = "+" + matcher.group(1);
				if (phoneNumber.startsWith(countryCode)) {
					return phoneNumber.substring(countryCode.length());
				}
				logger.warn("Derived country code does not prefix the phone number; returning it unchanged");
				return phoneNumber;
			}
		}
		logger.warn("Could not derive a country code from the phone schema regex; returning the phone number unchanged");
		return phoneNumber;
	}

	public static String getRegexForField(String fieldId) {
		return getRegexForField(fieldId, "en");
	}

	public static JSONArray getSignupSchemaArray() {
		JSONObject resp = null;
		try {
			resp = getSignupUISpecResponse().optJSONObject("response");
		} catch (Exception e) {
			return new JSONArray();
		}
		if (resp != null && resp.has("schema")) {
			return resp.optJSONArray("schema");
		}
		return new JSONArray();
	}

	public static String generateEmailFromRegex(String regex) {
		if (regex == null || regex.isEmpty()) {
			return "user" + System.currentTimeMillis() + "@example.com";
		}

		String localChars;
		if (regex.contains("A-Z") && regex.contains("a-z")) {
			localChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-";
		} else if (regex.contains("a-z")) {
			localChars = "abcdefghijklmnopqrstuvwxyz0123456789._-";
		} else if (regex.contains("A-Z")) {
			localChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-";
		} else {
			localChars = "abcdefghijklmnopqrstuvwxyz0123456789";
		}

		Random random = new Random();
		int localLength = 6 + random.nextInt(5);
		StringBuilder localPart = new StringBuilder();
		for (int i = 0; i < localLength; i++) {
			localPart.append(localChars.charAt(random.nextInt(localChars.length())));
		}

		String[] domains = { "gmail.com", "yahoo.com", "outlook.com", "example.com" };
		String domain = domains[random.nextInt(domains.length)];

		String email = localPart + "@" + domain;

		return email;
	}

	public static String generateValueFromRegex(String regex) {
		return generateValueFromRegex(regex, -1);
	}

	public static String generateValueFromRegex(String regex, int exactLength) {
		if (regex == null || regex.isEmpty()) {
			if (exactLength > 0) {
				throw new IllegalArgumentException("Regex is required when exactLength is specified");
			}
			return "defaultValue";
		}

		Random random = new Random();

		StringBuilder chars = new StringBuilder();
		if (regex.contains("A-Z"))
			chars.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
		if (regex.contains("a-z"))
			chars.append("abcdefghijklmnopqrstuvwxyz");
		if (regex.contains("0-9") || regex.contains("\\d"))
			chars.append("0123456789");

		if (chars.length() == 0) {
			chars.append("abcdefghijklmnopqrstuvwxyz");
		}

		int min = 8, max = 8;
		if (exactLength > 0) {
			min = max = exactLength;
		} else if (regex.contains("{") && regex.contains("}")) {
			String range = regex.substring(regex.indexOf('{') + 1, regex.indexOf('}'));
			String[] parts = range.split(",");
			try {
				if (parts.length == 2) {
					min = Integer.parseInt(parts[0].trim());
					max = Integer.parseInt(parts[1].trim());
				} else {
					min = max = Integer.parseInt(parts[0].trim());
				}
			} catch (NumberFormatException ignored) {
			}
		}

		int length = min + random.nextInt(Math.max(1, max - min + 1));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt(random.nextInt(chars.length())));
		}

		if (exactLength > 0 && sb.length() > 0 && sb.charAt(0) == '0' && chars.toString().equals("0123456789")) {
			sb.setCharAt(0, (char) ('1' + random.nextInt(9)));
		}

		return sb.toString();
	}

	public static Map<String, Map<String, Object>> getUiSpecFields() {
		Map<String, Map<String, Object>> fieldsMap = new LinkedHashMap<>();

		JSONObject response = getSignupUISpecResponse().optJSONObject("response");
		if (response == null)
			return fieldsMap;

		JSONArray schema = response.optJSONArray("schema");
		if (schema == null)
			return fieldsMap;

		for (int i = 0; i < schema.length(); i++) {
			JSONObject field = schema.optJSONObject(i);
			if (field == null)
				continue;

			String fieldId = field.optString("id", null);
			if (fieldId != null) {
				Map<String, Object> fieldDetails = field.toMap();
				fieldsMap.put(fieldId, fieldDetails);
			}
		}

		return fieldsMap;
	}

	public static String getRandomDOB() {
		LocalDate today = LocalDate.now();
		LocalDate earliest = today.minusYears(120);
		long daysRange = ChronoUnit.DAYS.between(earliest, today);

		long randomDays = ThreadLocalRandom.current().nextLong(daysRange);
		LocalDate dob = earliest.plusDays(randomDays);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH);
		return dob.format(formatter);
	}

	private static boolean getTriggerESignetKeyGenForPAR() {
		return triggerESignetKeyGenForPAR;
	}

	private static void setTriggerESignetKeyGenForPAR(boolean value) {
		triggerESignetKeyGenForPAR = value;
	}

	private static boolean getTriggerESignetKeyGenForPARPurposeLogin() {
		return triggerESignetKeyGenForPARPurposeLogin;
	}

	private static void setTriggerESignetKeyGenForPARPurposeLogin(boolean value) {
		triggerESignetKeyGenForPARPurposeLogin = value;
	}

	private static boolean getTriggerESignetKeyGenForPARPurposeLink() {
		return triggerESignetKeyGenForPARPurposeLink;
	}

	private static void setTriggerESignetKeyGenForPARPurposeLink(boolean value) {
		triggerESignetKeyGenForPARPurposeLink = value;
	}

	private static boolean getTriggerESignetKeyGenForPARPurposeNone() {
		return triggerESignetKeyGenForPARPurposeNone;
	}

	private static void setTriggerESignetKeyGenForPARPurposeNone(boolean value) {
		triggerESignetKeyGenForPARPurposeNone = value;
	}

	private static boolean getTriggerESignetKeyGenForPARNoPurpose() {
		return triggerESignetKeyGenForPARNoPurpose;
	}

	private static void setTriggerESignetKeyGenForPARNoPurpose(boolean value) {
		triggerESignetKeyGenForPARNoPurpose = value;
	}

	private static boolean getTriggerESignetKeyGenForPARNoTitle() {
		return triggerESignetKeyGenForPARNoTitle;
	}

	private static void setTriggerESignetKeyGenForPARNoTitle(boolean value) {
		triggerESignetKeyGenForPARNoTitle = value;
	}

	private static boolean getTriggerESignetKeyGenForPARSingleAcrValue() {
		return triggerESignetKeyGenForPARSingleAcrValue;
	}

	private static void setTriggerESignetKeyGenForPARSingleAcrValue(boolean value) {
		triggerESignetKeyGenForPARSingleAcrValue = value;
	}

	private static boolean getTriggerESignetKeyGenForPAREmptyTitle() {
		return triggerESignetKeyGenForPAREmptyTitle;
	}

	private static void setTriggerESignetKeyGenForPAREmptyTitle(boolean value) {
		triggerESignetKeyGenForPAREmptyTitle = value;
	}

	public static void getSupportedLanguage() {
		List<String> appLanguages = LanguageUtil.supportedLanguages;
		if (appLanguages == null || appLanguages.isEmpty()) {
			throw new IllegalStateException(
					"No supported languages found from the eSignet app locales (localeUrl + locales/default.json)");
		}
		BaseTestCase.languageList.clear();
		if (appLanguages.contains("eng")) {
			BaseTestCase.languageList.add("eng");
		}
		for (String lang : appLanguages) {
			if (!"eng".equals(lang) && !BaseTestCase.languageList.contains(lang)) {
				BaseTestCase.languageList.add(lang);
			}
		}
		logger.info("Supported Language (from application locales) = " + BaseTestCase.languageList);
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

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT$", OIDC_JWK_FOR_PAR);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR$", OIDC_JWK_FOR_PAR);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_LOGIN$",
				OIDC_JWK_FOR_PAR_PURPOSE_LOGIN);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_LOGIN$", OIDC_JWK_FOR_PAR_PURPOSE_LOGIN);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_LINK$",
				OIDC_JWK_FOR_PAR_PURPOSE_LINK);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_LINK$", OIDC_JWK_FOR_PAR_PURPOSE_LINK);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_VERIFY$",
				OIDC_JWK_FOR_PAR_PURPOSE_VERIFY);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_VERIFY$", OIDC_JWK_FOR_PAR_PURPOSE_VERIFY);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_NONE$",
				OIDC_JWK_FOR_PAR_PURPOSE_NONE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_NONE$", OIDC_JWK_FOR_PAR_PURPOSE_NONE);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_NO_PURPOSE$",
				OIDC_JWK_FOR_PAR_NO_PURPOSE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_NO_PURPOSE$", OIDC_JWK_FOR_PAR_NO_PURPOSE);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_NO_TITLE$",
				OIDC_JWK_FOR_PAR_NO_TITLE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_NO_TITLE$", OIDC_JWK_FOR_PAR_NO_TITLE);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_EMPTY_TITLE$",
				OIDC_JWK_FOR_PAR_EMPTY_TITLE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_EMPTY_TITLE$", OIDC_JWK_FOR_PAR_EMPTY_TITLE);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_SINGLE_ACR_VALUE$",
				OIDC_JWK_FOR_PAR_SINGLE_ACR_VALUE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_SINGLE_ACR_VALUE$",
				OIDC_JWK_FOR_PAR_SINGLE_ACR_VALUE);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PAR_REQUIRED$",
				OIDC_JWK_FOR_PAR_REQUIRED);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_REQUIRED$", OIDC_JWK_FOR_PAR_REQUIRED);

		if (jsonString.contains("$ESIGNET_REDIRECT_URI$")) {
			jsonString = replaceKeywordWithValue(jsonString, "$ESIGNET_REDIRECT_URI$",
					EsignetConfigManager.getproperty("baseurl") + "userprofile");
		}

		if (jsonString.contains("$POLICYNUMBERFORSUNBIRDRC$")) {
			jsonString = replaceKeywordWithValue(jsonString, "$POLICYNUMBERFORSUNBIRDRC$", getSunBirdRPolicyNumber());
		}

		if (jsonString.contains("$FULLNAMEFORSUNBIRDRC$")) {
			jsonString = replaceKeywordWithValue(jsonString, "$FULLNAMEFORSUNBIRDRC$", getSunBirdRFullName());
		}

		if (jsonString.contains("$DOBFORSUNBIRDRC$")) {
			jsonString = replaceKeywordWithValue(jsonString, "$DOBFORSUNBIRDRC$", getSunBirdRDob());
		}

		return jsonString;

	}

	private static String processClientAssertion(String jsonString, String placeholder, String jwkKeyName) {

		if (jsonString.contains(placeholder)) {

			String keyString = JWKKeyUtil.getJWKKey(jwkKeyName);
			if (keyString == null) {
				logger.warn("No cached JWK for key=" + jwkKeyName
						+ " - generating one now (its client-creation testcase was likely skipped)");
				JWKKeyUtil.generateAndCacheJWKKey(jwkKeyName);
				keyString = JWKKeyUtil.getJWKKey(jwkKeyName);
			}
			RSAKey rsaKey;

			try {
				rsaKey = RSAKey.parse(keyString);
			} catch (Exception e) {
				throw new RuntimeException(
						"Failed to parse JWK for placeholder " + placeholder + " (key=" + jwkKeyName + ")", e);
			}

			JSONObject root = new JSONObject(jsonString);
			String clientId = root.optString("client_id", null);
			String audKey = null;

			if (root.has("aud_key")) {
				audKey = root.optString("aud_key", null);
				root.remove("aud_key");
				jsonString = root.toString();
			}

			String url = getValueFromEsignetWellKnownEndPoint(audKey, EsignetConfigManager.getEsignetBaseUrl());

			if (clientId != null) {
				jsonString = replaceKeywordWithValue(jsonString, placeholder, signJWKKey(clientId, rsaKey, url));
			}
		}

		return jsonString;
	}

	private static final Set<String> generatedJwkKeys = ConcurrentHashMap.newKeySet();

	private static String processJWKKey(String jsonString, String placeholder, String jwkKeyName) {
		if (!jsonString.contains(placeholder))
			return jsonString;
		String jwkKey = generatedJwkKeys.add(jwkKeyName) ? JWKKeyUtil.generateAndCacheJWKKey(jwkKeyName)
				: JWKKeyUtil.getJWKKey(jwkKeyName);
		try {
			jwkKey = RSAKey.parse(jwkKey).toPublicJWK().toJSONString();
		} catch (Exception e) {
			throw new RuntimeException("Failed to derive public JWK for placeholder " + placeholder, e);
		}
		return replaceKeywordWithValue(jsonString, placeholder, jwkKey);
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


	private static JSONObject esignetDiscoveryDocument = null;

	/**
	 * The OIDC discovery document, fetched once per run. Never throws - some deployments (e.g. the
	 * Thunder/eSignet-go build) 500 on this endpoint server-side; on any failure this logs a warning
	 * and caches an empty JSONObject, so isParSupported()/isParRequired() fall back to their own safe
	 * defaults (false) below unless overridden via config.
	 */
	private static synchronized JSONObject getEsignetDiscoveryDocument() {
		if (esignetDiscoveryDocument != null) {
			return esignetDiscoveryDocument;
		}
		String url = EsignetConfigManager.getEsignetBaseUrl()
				+ EsignetConfigManager.getproperty("esignetWellKnownEndPoint");
		try {
			Response response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
			esignetDiscoveryDocument = new JSONObject(response.getBody().asString());
		} catch (Exception e) {
			logger.warn("Could not fetch/parse the eSignet discovery document from " + url
					+ " - assuming PAR is not supported/required. Check esignetWellKnownEndPoint in config. "
					+ "Cause: " + e.getMessage());
			esignetDiscoveryDocument = new JSONObject();
		}
		return esignetDiscoveryDocument;
	}

	/**
	 * Whether the environment supports the PAR flow, i.e. advertises a pushed authorization request
	 * endpoint in its discovery document, fetched from the configurable `esignetWellKnownEndPoint`.
	 * Falls back to false if the discovery document is unreachable.
	 */
	public static boolean isParSupported() {
		String parEndpoint = getEsignetDiscoveryDocument().optString("pushed_authorization_request_endpoint", "");
		return !parEndpoint.isEmpty();
	}

	/**
	 * Whether the environment mandates PAR for every client. When true, even clients that don't set
	 * require_pushed_authorization_requests must go through PAR - the direct /authorize flow is
	 * rejected server-side (see AuthorizationServiceImpl#assertPARRequiredIsFalse). Derived from the
	 * discovery document, fetched from the configurable `esignetWellKnownEndPoint`. Falls back to
	 * false if the discovery document is unreachable.
	 */
	public static boolean isParRequired() {
		return getEsignetDiscoveryDocument().optBoolean("require_pushed_authorization_requests", false);
	}

	/** KBI is unsupported only under mosipid. */
	public static boolean isKbiSupportedPlugin() {
		return !"mosipid".equalsIgnoreCase(getPluginName());
	}

	private static Integer idTokenExpirySeconds = null;

	public static int getIdTokenExpirySeconds() {
		if (idTokenExpirySeconds != null) {
			return idTokenExpirySeconds;
		}

		String configuredValue = EsignetConfigManager.getProperty("idTokenExpirySeconds", "").trim();
		if (!configuredValue.isEmpty()) {
			try {
				idTokenExpirySeconds = Integer.parseInt(configuredValue);
				return idTokenExpirySeconds;
			} catch (NumberFormatException e) {
				logger.warn("Ignoring idTokenExpirySeconds='" + configuredValue
						+ "' - not a valid integer; falling back to the eSignet actuator");
			}
		}

		String actuatorValue = isEsignetActuatorEnabled()
				? getValueFromEsignetActuator(EsignetConfigManager.getEsignetActuatorPropertySection(),
						GlobalConstants.MOSIP_ESIGNET_ID_TOKEN_EXPIRE_SECONDS)
				: null;
		if (actuatorValue == null || actuatorValue.isBlank()) {
			throw new IllegalStateException(
					"Could not resolve the ID token expiry seconds from config (idTokenExpirySeconds) or the "
							+ "eSignet actuator (mosip.esignet.id-token-expire-seconds). Set idTokenExpirySeconds "
							+ "in config.properties for environments without a working eSignet actuator.");
		}
		idTokenExpirySeconds = Integer.parseInt(actuatorValue);
		return idTokenExpirySeconds;
	}

	public static String signJWKKey(String clientId, RSAKey jwkKey, String tempUrl) {
		int idTokenExpirySecs = getIdTokenExpirySeconds();
		JWSSigner signer;

		try {
			signer = new RSASSASigner(jwkKey);

			Date currentTime = new Date();

			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentTime);

			calendar.add(Calendar.SECOND, idTokenExpirySecs);

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
		if (driver == null) {
			logger.error("WebDriver not initialized. Call startDriverWithNetwork() or setDriver() first.");
			return null;
		}
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

	public void writeConfigValueAndSkipIfProvided(String configKey, String testCaseName, String idKeyName) {
		String configValue = EsignetConfigManager.getproperty(configKey);
		if (configValue == null || configValue.trim().isEmpty()) {
			return;
		}
		String value = configValue.split(",")[0].trim();
		writeAutoGeneratedId(testCaseName, idKeyName, value);
		throw new SkipException(
				idKeyName + " value is provided in config, skipping " + testCaseName + " generation test case");
	}

	public static String isTestCaseValidForExecution(TestCaseDTO testCaseDTO) {
		String testCaseName = testCaseDTO.getTestCaseName();
		currentTestCaseName = testCaseName;

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

		String resolvedPluginName = getPluginName();
		if (resolvedPluginName.equals("mock")) {
			BaseTestCase.setSupportedIdTypes(Arrays.asList("UIN"));

			String endpoint = testCaseDTO.getEndPoint();
			boolean isSunbirdRegistryEndpoint = endpoint.startsWith("$SUNBIRDBASEURL$");
			if (!isSunbirdRegistryEndpoint && endpoint.contains("/esignet/") == false
					&& endpoint.contains("/mock-identity-system/") == false) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

		} else if (resolvedPluginName.equals("mosipid")) {
			getSupportedIdTypesValueFromActuator();

			logger.info("supportedIdType = " + supportedIdType);

			String endpoint = testCaseDTO.getEndPoint();
			if (endpoint.startsWith("$SUNBIRDBASEURL$")) {
				throw new SkipException("Skipped: " + testCaseName + " is a Sunbird RC registry call, not applicable under mosipid");
			}
			if (endpoint.contains("/mock-identity-system/") == true
					|| ((testCaseName.equals("ESignetUI_CreateOIDCClient_all_Valid_Smoke_sid"))
							&& endpoint.contains("/v1/esignet/client-mgmt/client"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

			String preconfiguredOidcClientId = EsignetConfigManager.getproperty("oidcClientId");
			if (preconfiguredOidcClientId != null && !preconfiguredOidcClientId.isBlank()
					&& OIDC_CLIENT_CHAIN_TESTCASE_PREFIXES.stream().anyMatch(modifiedTestCaseName::startsWith)) {
				throw new SkipException("oidcClientId is provided in config - skipping " + testCaseName
						+ " (only needed to create a new OIDC client)");
			}
		}
		return testCaseName;
	}

	private static final Set<String> OIDC_CLIENT_CHAIN_TESTCASE_PREFIXES = Set.of("DefinePolicyGroup_",
			"DefinePolicy_", "PublishPolicy_", "PartnerSelfRegistration_", "UploadCACertificate_",
			"UploadCInterCertificate_", "UploadPartnerCert_", "SubmitPartnerApiKeyRequest_",
			"ApproveRejectPartnerAPIKeyReq_");

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

	public static String generateParRequestUri(String clientIdKey, String clientAssertionPlaceholder)
			throws SecurityXSSException, JsonProcessingException {
		return generateParRequestUri(clientIdKey, clientAssertionPlaceholder, DEFAULT_ACR_VALUES);
	}

	public static String generateParRequestUri(String clientIdKey, String clientAssertionPlaceholder, String acrValues)
			throws SecurityXSSException, JsonProcessingException {
		return generateParRequestUri(clientIdKey, clientAssertionPlaceholder, acrValues, null);
	}

	/** @param uiLocales see {@link #generateDirectAuthorizeUrl(String, String, String)}. */
	public static String generateParRequestUri(String clientIdKey, String clientAssertionPlaceholder, String acrValues,
			String uiLocales) throws SecurityXSSException, JsonProcessingException {

		String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		String parUrl = baseUrl + EsignetConfigManager.getProperty("esignetParEndpoint", "/v1/esignet/oauth/par");

		org.json.simple.JSONObject claimRequest = getRequestJson(CLAIMS_REQUEST);
		JSONObject requestBody = new JSONObject();

		requestBody.put("display", display);
		requestBody.put("response_type", responseType);
		requestBody.put("nonce", "$UNIQUENONCEVALUEFORESIGNET$");
		if (clientIdKey == null || clientIdKey.isEmpty()) {
			clientIdKey = "$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$";
		}
		requestBody.put("client_id", AdminTestUtil.replaceIdWithAutogeneratedId(clientIdKey, "$ID:"));
		requestBody.put("requestTime", "$TIMESTAMP$");
		requestBody.put("client_assertion_type", client_assertion_type);
		requestBody.put("claim_locales", claim_locales);
		if (uiLocales != null && !uiLocales.isBlank()) {
			requestBody.put("ui_locales", uiLocales);
		}
		requestBody.put("claims", claimRequest.toString());
		requestBody.put("scope", scope);
		requestBody.put("acr_values", acrValues);
		requestBody.put("redirect_uri", "$ESIGNET_REDIRECT_URI$");
		requestBody.put("state", state);
		requestBody.put("client_assertion", clientAssertionPlaceholder);
		requestBody.put("prompt", prompt);
		requestBody.put("aud_key", aud_key);

		Response response = postWithBodyAndCookieForAutoGeneratedIdForUrlEncoded(parUrl, requestBody.toString());

		if (response == null) {
			throw new RuntimeException("PAR request failed: null response");
		}

		JSONObject responseJson = new JSONObject(response.asString());

		if (!responseJson.has("request_uri")) {
			logger.error("PAR response missing request_uri: " + responseJson.toString());
			throw new RuntimeException("PAR response missing request_uri");
		}

		return responseJson.getString("request_uri");
	}

	/**
	 * Builds a direct (non-PAR) /authorize URL, carrying every parameter in the query string rather
	 * than behind a request_uri. Requires no client_assertion, since the client isn't authenticated
	 * on this browser-facing leg. Only valid for clients that don't set
	 * require_pushed_authorization_requests - those are forced down the PAR flow server-side.
	 */
	public static final String DEFAULT_ACR_VALUES = "mosip:idp:acr:generated-code mosip:idp:acr:biometrics mosip:idp:acr:linked-wallet mosip:idp:acr:password";

	/** acr_value that maps to the KBI (Knowledge-Based Identity) login factor in eSignet. */
	public static final String KBI_ACR_VALUE = "mosip:idp:acr:knowledge";

	public static String generateDirectAuthorizeUrl(String clientId) throws SecurityXSSException {
		return generateDirectAuthorizeUrl(clientId, DEFAULT_ACR_VALUES, null);
	}

	public static String generateDirectAuthorizeUrl(String clientId, String acrValues) throws SecurityXSSException {
		return generateDirectAuthorizeUrl(clientId, acrValues, null);
	}

	/** @param uiLocales OIDC ui_locales (e.g. "en"); null to omit, as most callers do. */
	public static String generateDirectAuthorizeUrl(String clientId, String acrValues, String uiLocales)
			throws SecurityXSSException {
		return buildDirectAuthorizeUrl(clientId, scope, true, acrValues, uiLocales);
	}

	public static String generateDirectAuthorizeUrlWithoutClaims(String clientId, String customScope)
			throws SecurityXSSException {
		return buildDirectAuthorizeUrl(clientId, customScope, false, DEFAULT_ACR_VALUES, null);
	}

	private static String buildDirectAuthorizeUrl(String clientId, String requestedScope, boolean includeClaims,
			String acrValues, String uiLocales) throws SecurityXSSException {

		String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		String redirectUri = EsignetConfigManager.getproperty("baseurl") + "userprofile";
		String nonce = String.valueOf(Calendar.getInstance().getTimeInMillis());

		Charset utf8 = StandardCharsets.UTF_8;
		StringBuilder url = new StringBuilder(
				baseUrl + EsignetConfigManager.getProperty("esignetAuthorizeEndpoint", "/authorize") + "?");
		url.append("client_id=").append(URLEncoder.encode(clientId, utf8));
		url.append("&response_type=").append(responseType);
		url.append("&scope=").append(URLEncoder.encode(requestedScope, utf8));
		url.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, utf8));
		url.append("&display=").append(display);
		url.append("&prompt=").append(prompt);
		url.append("&nonce=").append(nonce);
		url.append("&state=").append(state);
		url.append("&acr_values=").append(URLEncoder.encode(acrValues, utf8));
		if (includeClaims) {
			org.json.simple.JSONObject claimRequest = getRequestJson(CLAIMS_REQUEST);
			url.append("&claims=").append(URLEncoder.encode(claimRequest.toString(), utf8));
		}
		if (uiLocales != null && !uiLocales.isBlank()) {
			url.append("&ui_locales=").append(URLEncoder.encode(uiLocales, utf8));
		}

		return url.toString();
	}

	private static String serverAuthenticatorPluginName = null;

	private static Boolean esignetActuatorEnabled = null;

	/** Single boolean-config-flag convention for this file: only "true" (case-insensitive) is truthy. */
	private static boolean parseConfiguredBoolean(String key, boolean defaultWhenBlank) {
		String value = EsignetConfigManager.getProperty(key, "").trim();
		return value.isEmpty() ? defaultWhenBlank : Boolean.parseBoolean(value);
	}

	public static boolean isEsignetActuatorEnabled() {
		if (esignetActuatorEnabled == null) {
			esignetActuatorEnabled = parseConfiguredBoolean("esignetActuatorEnabled", true);
		}
		return esignetActuatorEnabled;
	}

	public static String getIdentityPluginNameFromEsignetActuator() {
		if (serverAuthenticatorPluginName != null && !serverAuthenticatorPluginName.isBlank()) {
			return serverAuthenticatorPluginName;
		}
		if (!isEsignetActuatorEnabled()) {
			return null;
		}
		serverAuthenticatorPluginName = getValueFromEsignetActuator(ESignetConstants.CLASS_PATH_APPLICATION_PROPERTIES,
				"mosip.esignet.integration.authenticator");
		return serverAuthenticatorPluginName;
	}

	/**
	 * Whether the eSignet server's actual identity authenticator is Sunbird RC (server-side only).
	 * Prefers the local `sunbirdAuthenticatorActive` config override (true/false) to skip the
	 * actuator round-trip - needed for deployments with no actuator (esignetActuatorEnabled=false),
	 * where the actuator-only detection below always reports false regardless of the real server.
	 */
	public static boolean isSunbirdAuthenticatorActive() {
		String configuredValue = EsignetConfigManager.getProperty("sunbirdAuthenticatorActive", "").trim();
		if (!configuredValue.isEmpty()) {
			return parseConfiguredBoolean("sunbirdAuthenticatorActive", false);
		}

		String serverPlugin = getIdentityPluginNameFromEsignetActuator();
		return serverPlugin != null && serverPlugin.toLowerCase().contains("sunbirdrcauthenticationservice");
	}

	public static String generateParRequestWithoutNonceAndState() throws SecurityXSSException, JsonProcessingException {

		String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		String parUrl = baseUrl + EsignetConfigManager.getProperty("esignetParEndpoint", "/v1/esignet/oauth/par");

		org.json.simple.JSONObject claimRequest = getRequestJson(CLAIMS_REQUEST);
		JSONObject requestBody = new JSONObject();

		requestBody.put("display", display);
		requestBody.put("response_type", responseType);
		requestBody.put("client_id", AdminTestUtil
				.replaceIdWithAutogeneratedId("$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$", "$ID:"));
		requestBody.put("requestTime", "$TIMESTAMP$");
		requestBody.put("client_assertion_type", client_assertion_type);
		requestBody.put("claim_locales", claim_locales);
		requestBody.put("claims", claimRequest.toString());
		requestBody.put("scope", scope);
		requestBody.put("acr_values",
				"mosip:idp:acr:generated-code mosip:idp:acr:biometrics mosip:idp:acr:linked-wallet mosip:idp:acr:password");
		requestBody.put("redirect_uri", "$ESIGNET_REDIRECT_URI$");
		requestBody.put("client_assertion", "$CLIENT_ASSERTION_PAR_JWT$");
		requestBody.put("prompt", prompt);
		requestBody.put("aud_key", aud_key);

		Response response = postWithBodyAndCookieForAutoGeneratedIdForUrlEncoded(parUrl, requestBody.toString());

		if (response == null) {
			throw new RuntimeException("PAR request failed: null response");
		}

		JSONObject responseJson = new JSONObject(response.asString());

		if (!responseJson.has("request_uri")) {
			logger.error("PAR response missing request_uri: " + responseJson.toString());
			throw new RuntimeException("PAR response missing request_uri");
		}

		return responseJson.getString("request_uri");
	}


	private static JSONObject mockIdentitySchemaJson = null;

	private static final Properties MOCK_IDENTITY_VALUE_MAP = new Properties();

	static {
		try (InputStream is = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("config/mockIdentityValueMapping.properties")) {
			if (is == null) {
				throw new RuntimeException("mockIdentityValueMapping.properties NOT FOUND in classpath");
			}
			MOCK_IDENTITY_VALUE_MAP.load(is);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load mockIdentityValueMapping.properties", e);
		}
	}

	public static String getMockIdentitySchema() {
		try {
			if (mockIdentitySchemaJson != null) {
				return mockIdentitySchemaJson.toString();
			}

			String endpoint = properties.getProperty("mockIdentityIdentitySchemaEndpoint");
			String url = ApplnURI.replace("-internal", "") + endpoint;
			Response response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);

			mockIdentitySchemaJson = new JSONObject(response.asString());

			return mockIdentitySchemaJson.toString();
		} catch (Exception e) {
			throw new RuntimeException("Failed to fetch mock identity schema", e);
		}
	}

	public static String generateDynamicMockIdentityRequest(String schemaStr, String testCaseName) {
		try {
			JSONObject fullSchema = new JSONObject(schemaStr);
			JSONObject schemaRoot = fullSchema.has(GlobalConstants.RESPONSE)
					? fullSchema.getJSONObject(GlobalConstants.RESPONSE)
					: fullSchema;

			JSONObject identity = (JSONObject) processMockIdentitySchema(schemaRoot, fullSchema,
					GlobalConstants.REQUEST, null, true, testCaseName);

			return new JSONObject().put(GlobalConstants.REQUEST, identity)
					.put("requestTime", GlobalConstants.TIMESTAMP).toString();
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate dynamic request from schema", e);
		}
	}

	private static Object processMockIdentitySchema(JSONObject schema, JSONObject root, String field,
			String parentField, boolean required, String testCaseName) {

		schema = resolveMockIdentitySchema(schema, root);

		String type = schema.optString("type", "string");

		if (schema.has("type") && schema.get("type") instanceof JSONArray) {
			JSONArray types = schema.getJSONArray("type");
			for (int i = 0; i < types.length(); i++) {
				String candidate = types.getString(i);
				if ("array".equals(candidate) || "object".equals(candidate)) {
					type = candidate;
					break;
				}
			}
		}

		switch (type) {

		case "object":
			JSONObject obj = new JSONObject();
			JSONObject props = schema.optJSONObject("properties");
			JSONArray req = schema.optJSONArray("required");

			if (props != null) {
				for (String key : props.keySet()) {
					boolean isReq = req != null && req.toList().contains(key);
					obj.put(key, processMockIdentitySchema(props.getJSONObject(key), root, key, key, isReq,
							testCaseName));
				}
			}
			return obj;

		case "array":
			JSONArray arr = new JSONArray();
			JSONObject items = schema.optJSONObject("items");

			if (items == null && schema.has("allOf")) {
				JSONArray allOf = schema.getJSONArray("allOf");
				for (int i = 0; i < allOf.length(); i++) {
					JSONObject part = resolveMockIdentitySchema(allOf.getJSONObject(i), root);
					if (part.has("items")) {
						items = part.getJSONObject("items");
						break;
					}
				}
				// Fallback: some schemas express the items schema as the allOf entry itself
				// (no nested "items" wrapper) rather than wrapping it under an "items" key.
				if (items == null) {
					items = allOf.getJSONObject(allOf.length() - 1);
				}
			}

			if (items == null)
				return arr;

			items = resolveMockIdentitySchema(items, root);
			JSONObject propsArr = items.optJSONObject("properties");

			if (propsArr != null && propsArr.has("language") && propsArr.has("value")) {

				JSONArray langArr = new JSONArray();
				JSONObject row = new JSONObject();
				row.put("language", "eng");

				String keyToUse = resolveMockIdentitySchemaKey(field, parentField);

				String propValue = null;
				if (!"phone".equalsIgnoreCase(keyToUse) && !"individualId".equalsIgnoreCase(keyToUse)) {
					propValue = MOCK_IDENTITY_VALUE_MAP.getProperty(keyToUse);
					if (propValue == null) {
						propValue = MOCK_IDENTITY_VALUE_MAP.getProperty(keyToUse.toLowerCase());
					}
				}

				Object val;
				if (propValue != null && !propValue.trim().isEmpty()) {
					if ("fullname".equalsIgnoreCase(keyToUse) || "givenname".equalsIgnoreCase(keyToUse)
							|| "familyname".equalsIgnoreCase(keyToUse)) {
						propValue = propValue.replaceAll("[^a-zA-Z\\s]", "");
					}
					val = propValue;
				} else {
					val = generateMockIdentitySchemaValue(propsArr.getJSONObject("value"), keyToUse, testCaseName);
				}

				row.put("value", val);
				langArr.put(row);
				return langArr;
			}

			arr.put(processMockIdentitySchema(items, root, field, parentField, true, testCaseName));
			return arr;

		default:

			String keyToUse = resolveMockIdentitySchemaKey(field, parentField);

			String propValue = null;
			if (!"phone".equalsIgnoreCase(keyToUse) && !"individualId".equalsIgnoreCase(keyToUse)) {
				propValue = MOCK_IDENTITY_VALUE_MAP.getProperty(keyToUse);
				if (propValue == null) {
					propValue = MOCK_IDENTITY_VALUE_MAP.getProperty(keyToUse.toLowerCase());
				}
			}

			if (propValue != null && !propValue.trim().isEmpty()) {
				return propValue;
			}

			if ("email".equalsIgnoreCase(keyToUse)) {
				return testCaseName + "@mosip.net";
			}

			return generateMockIdentitySchemaValue(schema, keyToUse, testCaseName);
		}
	}

	private static Object generateMockIdentitySchemaValue(JSONObject schema, String field, String testCaseName) {

		if ("email".equalsIgnoreCase(field)) {
			return testCaseName + "@mosip.net";
		}

		if (!"phone".equalsIgnoreCase(field) && !"individualId".equalsIgnoreCase(field)) {
			String propValue = MOCK_IDENTITY_VALUE_MAP.getProperty(field);
			if (propValue == null) {
				propValue = MOCK_IDENTITY_VALUE_MAP.getProperty(field.toLowerCase());
			}
			if (propValue != null && !propValue.trim().isEmpty()) {
				if ("fullname".equalsIgnoreCase(field) || "givenname".equalsIgnoreCase(field)
						|| "familyname".equalsIgnoreCase(field)) {
					propValue = propValue.replaceAll("[^a-zA-Z\\s]", "");
				}
				return propValue;
			}
		}

		if (schema.has("pattern")) {
			try {
				String originalPattern = schema.getString("pattern");
				String strippedPattern = originalPattern.replaceAll("\\(\\?=.*?\\)", "");
				Pattern compiledOriginal = Pattern.compile(originalPattern);

				String candidate = null;
				for (int attempt = 0; attempt < 10; attempt++) {
					candidate = genStringAsperRegex(strippedPattern);
					if (compiledOriginal.matcher(candidate).matches()) {
						return candidate;
					}
				}
				logger.warn("Could not generate a value satisfying pattern '" + originalPattern
						+ "' after stripping lookaheads; falling back.");
			} catch (Exception e) {
				logger.warn("Could not generate a value for field '" + field + "' from schema pattern: "
						+ e.getMessage() + " - falling back to enum/literal");
			}
		}

		if (schema.has("enum")) {
			JSONArray arr = schema.getJSONArray("enum");
			return arr.get(0);
		}

		return "Test_" + field;
	}

	private static JSONObject resolveMockIdentitySchema(JSONObject schema, JSONObject root) {

		JSONObject result = new JSONObject();
		JSONObject effectiveRoot = root.has(GlobalConstants.RESPONSE) ? root.getJSONObject(GlobalConstants.RESPONSE)
				: root;

		if (schema.has("$ref")) {
			String ref = schema.getString("$ref");
			Object current = effectiveRoot;
			for (String part : ref.substring(2).split("/")) {
				current = ((JSONObject) current).get(part);
			}
			result = mergeMockIdentitySchema(result, (JSONObject) current);
		}

		if (schema.has("allOf")) {
			JSONArray arr = schema.getJSONArray("allOf");
			for (int i = 0; i < arr.length(); i++) {
				result = mergeMockIdentitySchema(result, resolveMockIdentitySchema(arr.getJSONObject(i), effectiveRoot));
			}
		}

		JSONObject copy = new JSONObject(schema.toString());
		copy.remove("$ref");
		copy.remove("allOf");

		return mergeMockIdentitySchema(result, copy);
	}

	private static JSONObject mergeMockIdentitySchema(JSONObject base, JSONObject override) {
		JSONObject result = new JSONObject(base.toString());
		for (String key : override.keySet()) {
			Object val = override.get(key);
			if (result.has(key) && result.get(key) instanceof JSONObject && val instanceof JSONObject) {
				result.put(key, mergeMockIdentitySchema(result.getJSONObject(key), (JSONObject) val));
			} else {
				result.put(key, val);
			}
		}
		return result;
	}

	private static String resolveMockIdentitySchemaKey(String field, String parentField) {
		if ("value".equalsIgnoreCase(field) || "items".equalsIgnoreCase(field) || "request".equalsIgnoreCase(field)) {
			return parentField != null ? parentField : field;
		}
		return field;
	}

	// Ported from apitest-commons' AdminTestUtil (unreleased 1.7.0) rather than depending on it
	// directly, since Maven Central only publishes through 1.6.0 (see PR #2432 review discussion).
	public void extractAndStoreMockIdentityDetails(String testCaseName, String requestBody) {
		try {
			JSONObject root = new JSONObject(requestBody);
			JSONObject request = root.has(GlobalConstants.REQUEST) ? root.getJSONObject(GlobalConstants.REQUEST)
					: root;

			String individualId = request.optString("individualId", null);
			String email = request.optString("email", null);
			String phone = request.optString("phone", null);

			if (individualId != null && !individualId.isEmpty()) {
				writeAutoGeneratedId(testCaseName, "UIN", individualId);
			}
			if (email != null && !email.isEmpty()) {
				writeAutoGeneratedId(testCaseName, "EMAIL", email);
			}
			if (phone != null && !phone.isEmpty()) {
				writeAutoGeneratedId(testCaseName, "PHONE", phone);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to extract identity details from request", e);
		}
	}

	public static String getIdentifierFieldId() {
		return getValueFromSignupActuator("applicationConfig: [classpath:/application-default.properties]",
				"mosip.signup.identifier.name");
	}
}