package utils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
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
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v134.network.Network;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.testng.SkipException;

import base.BasePage;
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

	// Sunbird RC policy fixture (KBI credentials), generated once per run and reused by both the
	// CreatePolicySunBirdR prerequisite and any scenario driving a real KBI login against it.
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

	// Static (non-randomized) fixture values embedded as literals in CreatePolicySunBirdR.yml - keep
	// these in sync with that file if either is changed.
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
	protected static final String OIDC_JWK_FOR_PAR_SECONDARY = "oidcJWKForPARSecondary";
	protected static RSAKey oidc_JWK_Key_For_PAR = null;
	protected static final String CLAIMS_REQUEST = "config/claims.json";

	public static org.json.simple.JSONObject getClaimsJsonSafely() {
		org.json.simple.JSONObject claimRequest = getRequestJson(CLAIMS_REQUEST);
		if (claimRequest == null) {
			logger.warn("claims.json could not be read (likely a transient resource-copy hiccup) - re-copying "
					+ "test resources and retrying once");
			io.mosip.testrig.apirig.testrunner.ExtractResource.copyCommonResources();
			claimRequest = getRequestJson(CLAIMS_REQUEST);
		}
		if (claimRequest == null) {
			logger.warn("claims.json still unreadable after retry - proceeding with an empty claims request");
			claimRequest = new org.json.simple.JSONObject();
		}
		return claimRequest;
	}

	private static final String display = "popup";
	private static final String responseType = "code";
	private static final String client_assertion_type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
	private static final String claim_locales = "en";
	private static final String scope = "openid profile";
	// Thunder/esignet-go has no authorization_scopes mapping for Manage-VID, so `openid Manage-VID`
	// alone produces an empty consent prompt and session_expired after OTP. `profile` is the
	// standard OIDC scope this server actually expands (via scope_claims) so the consent screen
	// still appears without a claims= query parameter. Manage-VID is kept in the request so the
	// Authorize Scopes section is asserted when an environment does map it.
	public static final String AUTHORIZE_SCOPE_ONLY = "openid profile Manage-VID";
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

	// True when running against the mock identity plugin (this environment). Callers use this to
	// bypass an individual check for a screen/element the mock plugin's simplified flow never
	// renders (no separate eKYC sequence, no real biometric device UI, etc.) WITHOUT aborting the
	// whole scenario via SkipException - just that one check is a no-op, everything else still runs.
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

	// Prefers the local `pluginToExecute` config value (mosipid/mock) to skip the actuator round-trip.
	// Falls back to actuator-based auto-detection - mirroring eSignet's own api-test getPluginName() -
	// when the property is unset or holds something other than mosipid/mock. Sunbird RC still counts
	// as "mock" here (same orchestration as plain mock); use isSunbirdAuthenticatorActive() to
	// distinguish them - that check queries the actuator directly regardless of this config value.
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
		// Blank means the actuator didn't answer - fall back to mosipid uncached so the next call retries it.
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

	// Hides AdminTestUtil.isCaptchaEnabled() (static methods don't override, so an unqualified call
	// from this class resolves here first) - the inherited version throws NullPointerException when
	// the actuator can't answer (getValueFromEsignetActuator returns null), which was failing every
	// single test case via isTestCaseValidForExecution() below on deployments with no working
	// actuator. Prefers the local `captchaEnabled` config value; falls back to the actuator only when
	// it's enabled, and otherwise assumes captcha is off rather than crash.
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

	// Builds a target length in [minLength, maxLength], floored at 2 so a single-char name is
	// never requested even if the schema's min is looser than that.
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
		return getPrerequisiteIdentityPhoneForLogin(true);
	}

	/**
	 * Phone number for OTP login tied to the AddIdentity prerequisite, optionally honouring the
	 * {@code uinPhoneNumber} config short-circuit used elsewhere in the suite.
	 */
	public static String getPrerequisiteIdentityPhoneForLogin(boolean honourUinPhoneNumberConfig) {
		if (honourUinPhoneNumberConfig) {
			String configuredPhoneNumber = EsignetConfigManager.getproperty("uinPhoneNumber");
			if (configuredPhoneNumber != null && !configuredPhoneNumber.isBlank()) {
				return configuredPhoneNumber.trim();
			}
		}

		boolean isMock = "mock".equalsIgnoreCase(getPluginName());
		// For mock, the mock-identity-system's send-otp lookup only recognizes an exact
		// individualId match - it doesn't look identities up by their separately-generated "phone"
		// attribute, which is an independently random, unrelated value for this schema. So the
		// login value has to be individualId (cached under "UIN"), which happens to be
		// phone-shaped for this schema, not the "phone" field itself.
		String cacheKey = isMock ? "AddIdentity_Valid_Parameters_smoke_Pos_UIN"
				: "AddIdentity_withValidParameters_smoke_Pos_PHONE";
		String phoneNumber = autoGeneratedIDValueCache.get(cacheKey);
		// phoneSchemaRegex is only ever populated for mosipid (AdminTestUtil.getRequiredField(),
		// called only on that branch in Runner.main()) - for mock it stays empty, so stripping must
		// come from the mock identity schema's own field pattern instead.
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

	/**
	 * Value typed into the UIN/VID OTP field. Thunder's UIN prompt is digits-only with no postfix
	 * (mobile uses {@code @phone}). Prefer the password-login UIN which is the documented
	 * pre-provisioned identity on this environment; MOSIP-style {@code vid} is unknown to mock.
	 */
	public static String getPrerequisiteVidForOtpLogin() {
		if (isMockPlugin()) {
			String passwordUin = getPasswordLoginUin();
			if (passwordUin != null && !passwordUin.isBlank()) {
				return passwordUin.trim();
			}
			String mockUin = getMockUinForPasswordLogin();
			if (mockUin != null && !mockUin.isBlank()) {
				return mockUin.trim();
			}
			String uin = getPrerequisiteUin();
			if (uin != null && !uin.isBlank()) {
				return uin.trim();
			}
		}
		String vid = getPrerequisitePerpetualVid();
		if (vid != null && !vid.isBlank()) {
			return vid.trim();
		}
		String uin = getPrerequisiteUin();
		return uin != null && !uin.isBlank() ? uin.trim() : null;
	}

	/**
	 * Second UIN/VID OTP login in the prefix/postfix scenario. Mock reuses the same individualId so
	 * consent is not requested again; mosipid uses the temporary VID when configured.
	 */
	public static String getPrerequisiteVid2ForOtpLogin() {
		if (isMockPlugin()) {
			return getPrerequisiteVidForOtpLogin();
		}
		String vid2 = getPrerequisiteTemporaryVid();
		if (vid2 != null && !vid2.isBlank()) {
			return vid2.trim();
		}
		return getPrerequisiteVidForOtpLogin();
	}

	public static String getPrerequisiteUin() {
		String configuredUins = EsignetConfigManager.getproperty("uin");
		if (configuredUins != null && !configuredUins.isBlank()) {
			return configuredUins.split(",")[0].trim();
		}
		String cachedUin = autoGeneratedIDValueCache.get("AddIdentity_withValidParameters_smoke_Pos_UIN");
		return cachedUin != null ? cachedUin.trim() : null;
	}

	/**
	 * Individual ID typed into the biometric UIN/VID field. Mock-identity-system kyc-auth looks
	 * identities up by exact {@code individualId} ({@code mockUin}), not the MOSIP {@code uin}
	 * used by OTP/phone scenarios on this environment.
	 */
	public static String getPrerequisiteUinForBiometricLogin() {
		if (isMockPlugin()) {
			String mockUin = getMockUinForPasswordLogin();
			if (mockUin != null && !mockUin.isBlank()) {
				return mockUin;
			}
			String cachedMockUin = autoGeneratedIDValueCache.get("AddIdentity_Valid_Parameters_smoke_Pos_UIN");
			if (cachedMockUin != null && !cachedMockUin.isBlank()) {
				return cachedMockUin.trim();
			}
		}
		return getPrerequisiteUin();
	}

	/** Pre-provisioned UIN for password-login scenarios ({@code passwordLoginUin} in config.properties). */
	public static String getPasswordLoginUin() {
		String uin = EsignetConfigManager.getproperty("passwordLoginUin");
		return uin != null && !uin.isBlank() ? uin.trim() : null;
	}

	/** Mock-identity individualId used as an alternate password-login UIN ({@code mockUin}). */
	public static String getMockUinForPasswordLogin() {
		String mockUin = EsignetConfigManager.getproperty("mockUin");
		return mockUin != null && !mockUin.isBlank() ? mockUin.trim() : null;
	}

	public static String getPasswordLoginEmail() {
		String email = EsignetConfigManager.getproperty("emailLoginId");
		return email != null && !email.isBlank() ? email.trim() : null;
	}

	/** Shared password for all pre-provisioned password-login identities in config.properties. */
	public static String getPasswordLoginPassword() {
		String password = EsignetConfigManager.getproperty("passwordLoginPassword");
		if (password == null || password.isBlank()) {
			password = EsignetConfigManager.getproperty("passwordForAddIdentity");
		}
		return password != null && !password.isBlank() ? password.trim() : null;
	}

	public static String resolveConfiguredPasswordLoginId(String identityKey) {
		if (identityKey == null || identityKey.isBlank()) {
			return null;
		}
		return switch (identityKey.trim().toLowerCase()) {
			case "mockuin" -> getMockUinForPasswordLogin();
			case "passwordloginuin" -> getPasswordLoginUin();
			case "emailloginid" -> getPasswordLoginEmail();
			default -> {
				String value = EsignetConfigManager.getproperty(identityKey.trim());
				yield value != null && !value.isBlank() ? value.trim() : null;
			}
		};
	}

	public static String getPrerequisiteInfantUin() {
		String configuredInfantUin = EsignetConfigManager.getproperty("infantUin");
		if (configuredInfantUin != null && !configuredInfantUin.isBlank()) {
			return configuredInfantUin.split(",")[0].trim();
		}
		String cachedUin = autoGeneratedIDValueCache.get("AddIdentity_Infant_smoke_Pos_UIN");
		return cachedUin != null ? cachedUin.trim() : null;
	}

	public static boolean arePrerequisiteUinAvailable() {
		String uin = getPrerequisiteUin();
		return uin != null && !uin.isBlank();
	}

	public static boolean arePrerequisiteVidsAvailable() {
		String vid1 = getPrerequisitePerpetualVid();
		String vid2 = getPrerequisiteTemporaryVid();
		return vid1 != null && !vid1.isBlank() && vid2 != null && !vid2.isBlank();
	}

	/**
	 * Reads a component host from {@code mosip_components_base_urls} (e.g. {@code idauthentication}).
	 */
	public static String getMosipComponentBaseUrl(String componentName) {
		String mapping = EsignetConfigManager.getproperty("mosip_components_base_urls");
		if (mapping == null || mapping.isBlank() || componentName == null || componentName.isBlank()) {
			return null;
		}
		for (String part : mapping.split(";")) {
			String[] keyValue = part.trim().split("=", 2);
			if (keyValue.length == 2 && componentName.equalsIgnoreCase(keyValue[0].trim())) {
				return keyValue[1].trim();
			}
		}
		return null;
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

	// ConcurrentHashMap, not HashMap - mutated via computeIfAbsent() below from a
	// @DataProvider(parallel = true) context (see runners.Runner#scenarios), so plain HashMap under
	// concurrent access can corrupt.
	private static final Map<String, String> mockIdentityFieldPatternCache = new ConcurrentHashMap<>();

	// Extracted via a text search over the raw schema rather than a structured JSON walk with
	// $ref/allOf resolution, since the mock identity schema's exact nesting/$ref layout isn't
	// known here - this finds a property with an adjacent "pattern" attribute wherever it appears,
	// which covers the common case without needing to correctly walk the full schema.
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

	// Matches a literal '+' - optionally immediately closed by a ']' when the schema writes it as
	// a single-char class, e.g. "^[+]91([1-9][0-9]{7,9})$" - followed by the run of digits after
	// it. Also covers plainer forms like "^\+91[1-9]\d{9}$" or "^\+(91)\d{10}$".
	private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("\\+\\]?(\\d+)");

	/**
	 * Converts a config-supplied local phone (uinPhoneNumber) to E.164 for ID Repository identity
	 * creation, using the same country-code derivation as {@link #stripCountryCode}.
	 */
	public static String formatLocalPhoneForIdentity(String localPhone, String schemaRegex) {
		if (localPhone == null || localPhone.isBlank()) {
			return localPhone;
		}
		String trimmed = localPhone.trim();
		if (trimmed.startsWith("+")) {
			return trimmed;
		}
		if (schemaRegex != null && !schemaRegex.isBlank()) {
			Matcher matcher = COUNTRY_CODE_PATTERN.matcher(schemaRegex);
			if (matcher.find()) {
				return "+" + matcher.group(1) + trimmed;
			}
		}
		logger.warn("Could not derive country code for local phone; using local number as-is for identity");
		return trimmed;
	}

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

		// exactLength > 0 is only ever passed for the mobile-number field (see
		// ConsentStepDefinition.userEnterValidMobileNumber), where the live signup form rejects a
		// leading zero regardless of what the fetched regex literally spells out - so this is safe
		// to apply unconditionally rather than trying to detect the constraint from the regex text.
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

		// yyyy/MM/dd matches the dateOfBirth format MOSIP identity records actually expect (see
		// AddIdentity.yml's hardcoded "1992/04/15") - the previous dd-MM-yyyy ordering produced a
		// plausible-looking but field-order-wrong date, which IDA's backend later fails to parse
		// when extracting birth year for its anonymous profile analytics (KER-UTL-103). Locale is
		// pinned explicitly - ofPattern() without one uses the JVM's default locale, and some
		// locales render numeric fields with non-ASCII digit glyphs, which would silently break
		// this on a machine/CI runner whose default locale differs from the one this was tested on.
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
		// Languages come from the eSignet app itself (locales/default.json via LanguageUtil); this drives
		// the mock identity's per-language fields (e.g. fullName). Fail fast if the app exposes none.
		List<String> appLanguages = LanguageUtil.supportedLanguages;
		if (appLanguages == null || appLanguages.isEmpty()) {
			throw new IllegalStateException(
					"No supported languages found from the eSignet app locales (localeUrl + locales/default.json)");
		}
		BaseTestCase.languageList.clear();
		if (appLanguages.contains("eng")) {
			BaseTestCase.languageList.add("eng"); // eng first so $1STLANG$/preferredLang is deterministic
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

		// PURPOSE_LOGIN
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_LOGIN$",
				OIDC_JWK_FOR_PAR_PURPOSE_LOGIN);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_LOGIN$", OIDC_JWK_FOR_PAR_PURPOSE_LOGIN);

		// PURPOSE_LINK
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_LINK$",
				OIDC_JWK_FOR_PAR_PURPOSE_LINK);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_LINK$", OIDC_JWK_FOR_PAR_PURPOSE_LINK);

		// PURPOSE_VERIFY
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_VERIFY$",
				OIDC_JWK_FOR_PAR_PURPOSE_VERIFY);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_VERIFY$", OIDC_JWK_FOR_PAR_PURPOSE_VERIFY);

		// PURPOSE_NONE
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PURPOSE_NONE$",
				OIDC_JWK_FOR_PAR_PURPOSE_NONE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_PURPOSE_NONE$", OIDC_JWK_FOR_PAR_PURPOSE_NONE);

		// NO PURPOSE
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_NO_PURPOSE$",
				OIDC_JWK_FOR_PAR_NO_PURPOSE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_NO_PURPOSE$", OIDC_JWK_FOR_PAR_NO_PURPOSE);

		// NO TITLE
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_NO_TITLE$",
				OIDC_JWK_FOR_PAR_NO_TITLE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_NO_TITLE$", OIDC_JWK_FOR_PAR_NO_TITLE);

		// EMPTY TITLE
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_EMPTY_TITLE$",
				OIDC_JWK_FOR_PAR_EMPTY_TITLE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_EMPTY_TITLE$", OIDC_JWK_FOR_PAR_EMPTY_TITLE);

		// SINGLE AUTH FACTOR
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_SINGLE_ACR_VALUE$",
				OIDC_JWK_FOR_PAR_SINGLE_ACR_VALUE);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_SINGLE_ACR_VALUE$",
				OIDC_JWK_FOR_PAR_SINGLE_ACR_VALUE);

		// PAR-MANDATED CLIENT (require_pushed_authorization_requests = true)
		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_PAR_REQUIRED$",
				OIDC_JWK_FOR_PAR_REQUIRED);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_REQUIRED$", OIDC_JWK_FOR_PAR_REQUIRED);

		jsonString = processClientAssertion(jsonString, "$CLIENT_ASSERTION_PAR_JWT_SECONDARY$",
				OIDC_JWK_FOR_PAR_SECONDARY);

		jsonString = processJWKKey(jsonString, "$OIDC_JWK_KEY_PAR_SECONDARY$", OIDC_JWK_FOR_PAR_SECONDARY);

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
				// This key is normally generated as a side effect of its matching client-creation
				// testcase running (see processJWKKey). If that testcase was skipped - e.g. a
				// pre-existing client ID was supplied via config (oidcClientId) instead of creating a
				// new client - the key was never generated, and getJWKKey (a pure cache read) returns
				// null. Generate one now so callers that unconditionally need a signed client
				// assertion (e.g. PAR request building in InvalidUrlStepDefinition) don't crash. It
				// won't match whatever key the referenced pre-existing client was actually registered
				// with, but callers relying on this fallback aren't validating signature correctness.
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
		// The cached key includes private material (needed later for client_assertion signing via
		// processClientAssertion); only the public JWK should ever be submitted as a client's
		// registered "publicKey".
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

	/* ======================= PAR SUPPORT DETECTION ======================= */

	private static JSONObject esignetDiscoveryDocument = null;

	/**
	 * The OIDC discovery document, fetched once per run from the configurable `esignetWellKnownEndPoint`.
	 * Never throws - some deployments (e.g. the Thunder/eSignet-go build) 500 on this endpoint
	 * server-side; on any failure this logs a warning and caches an empty JSONObject, so
	 * isParSupported()/isParRequired() fall back to their own safe defaults (false) below.
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
	 * endpoint in its discovery document.
	 */
	public static boolean isParSupported() {
		String parEndpoint = getEsignetDiscoveryDocument().optString("pushed_authorization_request_endpoint", "");
		return !parEndpoint.isEmpty();
	}

	/**
	 * Whether the environment mandates PAR for every client. When true, even clients that don't set
	 * require_pushed_authorization_requests must go through PAR - the direct /authorize flow is
	 * rejected server-side (see AuthorizationServiceImpl#assertPARRequiredIsFalse).
	 */
	public static boolean isParRequired() {
		return getEsignetDiscoveryDocument().optBoolean("require_pushed_authorization_requests", false);
	}

	/** KBI is unsupported only under mosipid. */
	public static boolean isKbiSupportedPlugin() {
		return !"mosipid".equalsIgnoreCase(getPluginName());
	}

	private static Integer idTokenExpirySeconds = null;

	// Prefers the local `idTokenExpirySeconds` config value to skip the actuator round-trip - some
	// eSignet deployments (e.g. the Thunder/eSignet-go build) don't expose a Spring actuator at all,
	// so mosip.esignet.id-token-expire-seconds is never resolvable there. Falls back to the actuator
	// when the property is unset.
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

	/** Every captured browser network request whose URL contains {@code urlSubstring} (e.g. "oauth-details"). */
	public static List<JSONObject> getPerformanceLogRequestsContaining(WebDriver driver, String urlSubstring) {
		List<JSONObject> matches = new ArrayList<>();
		LogEntries logs = driver.manage().logs().get("performance");
		for (LogEntry log : logs) {
			try {
				JSONObject msg = new JSONObject(log.getMessage());
				JSONObject request = msg.getJSONObject("message").getJSONObject("params").optJSONObject("request");
				if (request != null && request.optString("url", "").contains(urlSubstring)) {
					matches.add(request);
				}
			} catch (Exception ignored) {
			}
		}
		return matches;
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

	public void writeSecondaryConfigValueAndSkipIfProvided(String configKey, String testCaseName, String idKeyName) {
		String configValue = EsignetConfigManager.getproperty(configKey);
		if (configValue == null || !configValue.contains(",")) {
			return;
		}
		String secondary = configValue.split(",", 2)[1].trim();
		if (secondary.isEmpty()) {
			return;
		}
		writeAutoGeneratedId(testCaseName, idKeyName, secondary);
		throw new SkipException(
				idKeyName + " secondary value is provided in config, skipping " + testCaseName + " generation test case");
	}

	/**
	 * True when config supplies the minimum login identity (uin + phone + vid) so UI scenarios can
	 * proceed even if Keycloak/PMS provisioning fails (e.g. invalid IAM credentials in CI).
	 */
	public static boolean canRunMosipidUiWithPreconfiguredIdentity() {
		String uin = EsignetConfigManager.getproperty("uin");
		String vid = EsignetConfigManager.getproperty("vid");
		String phone = EsignetConfigManager.getproperty("uinPhoneNumber");
		return uin != null && !uin.isBlank()
				&& vid != null && !vid.isBlank()
				&& phone != null && !phone.isBlank();
	}

	/**
	 * Cache keys for OIDC clients that default and purpose-type scenarios resolve. When
	 * {@code oidcClientId} is preconfigured, the prerequisite suite does not create these clients,
	 * so they are seeded from that same ID instead of skipping at {@link #resolveClientId(String)}.
	 * PAR-mandated clients are excluded — they are not interchangeable with the default client.
	 * The secondary (TC_07) client is seeded from {@code oidcClientId}'s comma-separated second
	 * value when present, otherwise from the same primary ID so the scenario can still run.
	 */
	private static final List<String> PRECONFIGURED_PRIMARY_OIDC_CLIENT_CACHE_KEYS = List.of(
			"CreateOIDCClient_all_Valid_Smoke_sid_clientId",
			"CreateOIDCClient_with_purpose_type_login_Smoke_sid_clientId",
			"CreateOIDCClient_with_purpose_type_link_Smoke_sid_clientId",
			"CreateOIDCClient_with_purpose_type_verify_Smoke_sid_clientId",
			"CreateOIDCClient_with_purpose_type_none_Smoke_sid_clientId",
			"CreateOIDCClient_with_no_purpose_Smoke_sid_clientId",
			"CreateOIDCClient_with_purpose_title_and_subtitle_null_Smoke_sid_clientId",
			"CreateOIDCClient_with_purpose_title_and_subtitle_empty_Smoke_sid_clientId",
			"CreateOIDCClient_with_single_auth_factor_Smoke_sid_clientId");

	/** Single ACR requested for {@code @SingleAuthFactor} when the shared preconfigured client is used. */
	public static final String SINGLE_AUTH_FACTOR_ACR_VALUE = "mosip:idp:acr:generated-code";

	/**
	 * Seeds {@link AdminTestUtil#autoGeneratedIDValueCache} from config.properties so UI scenarios
	 * can run when prerequisite API calls fail or are skipped (e.g. pre-existing oidcClientId).
	 */
	public static void seedPreconfiguredIdsFromConfig() {
		String primaryClientId = getPreconfiguredPrimaryOidcClientId();
		if (primaryClientId != null) {
			for (String cacheKey : PRECONFIGURED_PRIMARY_OIDC_CLIENT_CACHE_KEYS) {
				autoGeneratedIDValueCache.put(cacheKey, primaryClientId);
			}
			logger.info("Seeded preconfigured oidcClientId into autogen cache for "
					+ PRECONFIGURED_PRIMARY_OIDC_CLIENT_CACHE_KEYS.size() + " client keys");
			String secondaryClientId = getPreconfiguredSecondaryOidcClientId();
			if (secondaryClientId == null) {
				secondaryClientId = primaryClientId;
			}
			autoGeneratedIDValueCache.put("CreateOIDCClient_secondary_Smoke_sid_clientId", secondaryClientId);
			logger.info("Seeded secondary oidcClientId into autogen cache: " + secondaryClientId);
		}

		String configuredUins = EsignetConfigManager.getproperty("uin");
		if (configuredUins != null && !configuredUins.isBlank()) {
			String uin = configuredUins.split(",")[0].trim();
			autoGeneratedIDValueCache.put("AddIdentity_withValidParameters_smoke_Pos_UIN", uin);
			autoGeneratedIDValueCache.put("AddIdentity_Vid_Generation_smoke_Pos_UIN", uin);
			logger.info("Seeded preconfigured uin into autogen cache");
		}

		String configuredInfantUin = EsignetConfigManager.getproperty("infantUin");
		if (configuredInfantUin != null && !configuredInfantUin.isBlank()) {
			autoGeneratedIDValueCache.put("AddIdentity_Infant_smoke_Pos_UIN",
					configuredInfantUin.split(",")[0].trim());
			logger.info("Seeded preconfigured infantUin into autogen cache");
		}

		String configuredVids = EsignetConfigManager.getproperty("vid");
		if (configuredVids != null && !configuredVids.isBlank()) {
			String[] vidList = configuredVids.split(",");
			if (vidList.length > 0 && !vidList[0].trim().isEmpty()) {
				autoGeneratedIDValueCache.put(PERPETUAL_VID_CACHE_KEY, vidList[0].trim());
			}
			if (vidList.length > 1 && !vidList[1].trim().isEmpty()) {
				autoGeneratedIDValueCache.put(TEMPORARY_VID_CACHE_KEY, vidList[1].trim());
			}
			logger.info("Seeded preconfigured vid into autogen cache");
		}
	}

	/** Primary {@code oidcClientId} from config, or {@code null} when unset. */
	public static String getPreconfiguredPrimaryOidcClientId() {
		String configured = EsignetConfigManager.getproperty("oidcClientId");
		if (configured == null || configured.isBlank()) {
			return null;
		}
		String primary = configured.split(",")[0].trim();
		return primary.isEmpty() ? null : primary;
	}

	/**
	 * Dedicated secondary {@code oidcClientId} ({@code primary,secondary} in config), or
	 * {@code null} when only a single client ID is configured.
	 */
	public static String getPreconfiguredSecondaryOidcClientId() {
		String configured = EsignetConfigManager.getproperty("oidcClientId");
		if (configured == null || !configured.contains(",")) {
			return null;
		}
		String secondary = configured.split(",", 2)[1].trim();
		return secondary.isEmpty() ? null : secondary;
	}

	public static boolean isPreconfiguredPrimaryOidcClient(String clientId) {
		String primary = getPreconfiguredPrimaryOidcClientId();
		return primary != null && primary.equals(clientId);
	}

	private static boolean isParRequiredClientKey(String clientIdKey) {
		return clientIdKey != null && clientIdKey.contains("CreateOIDCClient_par_required_");
	}

	/**
	 * Resolves a $ID:...$ client-id placeholder, falling back to config when the prerequisite cache
	 * is empty (common when oidcClientId is preconfigured and OIDC client creation was skipped).
	 */
	public static String resolveClientId(String clientIdKey) {
		if (ConsentDbUtil.SECONDARY_CLIENT_ID_KEY.equals(clientIdKey)) {
			String secondary = getPreconfiguredSecondaryOidcClientId();
			if (secondary != null) {
				return secondary;
			}
		} else if (!isParRequiredClientKey(clientIdKey)) {
			String primary = getPreconfiguredPrimaryOidcClientId();
			if (primary != null) {
				return primary;
			}
		}

		try {
			String resolved = AdminTestUtil.replaceIdWithAutogeneratedId(clientIdKey, "$ID:");
			if (resolved != null && !resolved.isBlank() && !resolved.contains("$ID:")) {
				return resolved;
			}
		} catch (Exception e) {
			logger.warn("Client ID cache miss for " + clientIdKey + ": " + e.getMessage());
		}

		// TC_07: no dedicated second client was created — reuse the existing oidcClientId.
		if (ConsentDbUtil.SECONDARY_CLIENT_ID_KEY.equals(clientIdKey)) {
			String primary = getPreconfiguredPrimaryOidcClientId();
			if (primary != null) {
				logger.info("No secondary OIDC client in cache or config - using existing oidcClientId '"
						+ primary + "'");
				ExtentReportManager.logStep(
						"No secondary OIDC client configured - using existing oidcClientId: " + primary);
				return primary;
			}
		}

		String reason = "No client ID in cache or config for " + clientIdKey
				+ " - this client is only created by esignetPrerequisiteSuite.xml, which did not run "
				+ "(runPrerequisiteSuite=false) or does not create this client; configure a pre-existing "
				+ "client ID or enable the prerequisite suite.";
		ExtentReportManager.logStep("⚠️ " + reason);
		throw new SkipException(reason);
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

		// Via getPluginName(), not the static field - the field can stay null on an uncached fallback.
		String resolvedPluginName = getPluginName();
		if (resolvedPluginName.equals("mock")) {
			BaseTestCase.setSupportedIdTypes(Arrays.asList("UIN"));

			String endpoint = testCaseDTO.getEndPoint();
			// Sunbird RC registry endpoints (CreatePolicySunBirdR/DeletePolicySunBirdR) are external to
			// eSignet/mock-identity-system - let them through to their own sunbird-authenticator gate
			// instead of skipping here unconditionally.
			boolean isSunbirdRegistryEndpoint = endpoint.startsWith("$SUNBIRDBASEURL$");
			if (!isSunbirdRegistryEndpoint && endpoint.contains("/esignet/") == false
					&& endpoint.contains("/mock-identity-system/") == false) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

		} else if (resolvedPluginName.equals("mosipid")) {
			getSupportedIdTypesValueFromActuator();

			logger.info("supportedIdType = " + supportedIdType);

			String endpoint = testCaseDTO.getEndPoint();
			// Sunbird RC is never relevant under mosipid (a real idrepo-backed identity, not
			// mock/sunbird), so skip it here with a clear reason rather than falling through.
			if (endpoint.startsWith("$SUNBIRDBASEURL$")) {
				throw new SkipException("Skipped: " + testCaseName + " is a Sunbird RC registry call, not applicable under mosipid");
			}
			if (endpoint.contains("/mock-identity-system/") == true
					|| ((testCaseName.equals("ESignetUI_CreateOIDCClient_all_Valid_Smoke_sid"))
							&& endpoint.contains("/v1/esignet/client-mgmt/client"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

			// DefinePolicyGroup -> DefinePolicy -> PublishPolicy -> CreatePartner -> UploadCACertificate
			// -> UploadPartnerCert -> RequestAPIKeyForAuthPartner -> ApproveAPIKey exist solely to feed
			// OIDCClient's real (mosipid) client creation - nothing else in the suite consumes their
			// output. Skip this chain only when both primary and secondary OIDC clients are supplied
			// via config; a lone primary still needs the chain so TC_07 can create the secondary client.
			String preconfiguredOidcClientId = EsignetConfigManager.getproperty("oidcClientId");
			if (preconfiguredOidcClientId != null && !preconfiguredOidcClientId.isBlank()
					&& getPreconfiguredSecondaryOidcClientId() != null
					&& OIDC_CLIENT_CHAIN_TESTCASE_PREFIXES.stream().anyMatch(modifiedTestCaseName::startsWith)) {
				throw new SkipException("oidcClientId primary,secondary is provided in config - skipping "
						+ testCaseName + " (only needed to create new OIDC clients)");
			}
		}
		return testCaseName;
	}

	// modifiedTestCaseName prefixes (i.e. testCaseName with its module prefix stripped, matching
	// AdminTestUtil#getAutogenIdKeyName's convention) for every prerequisite that exists solely to
	// feed OIDCClient's mosipid client creation.
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

		org.json.simple.JSONObject claimRequest = getClaimsJsonSafely();
		JSONObject requestBody = new JSONObject();

		requestBody.put("display", display);
		requestBody.put("response_type", responseType);
		requestBody.put("nonce", "$UNIQUENONCEVALUEFORESIGNET$");
		if (clientIdKey == null || clientIdKey.isEmpty()) {
			clientIdKey = "$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$";
		}
		// resolveClientId() throws a clear SkipException (instead of silently sending an unresolved
		// "$ID:...$" placeholder as client_id, which the PAR endpoint rejects with a confusing failure)
		// when this key's client was never created and oidcClientId is not configured to fall back to.
		requestBody.put("client_id", resolveClientId(clientIdKey));
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
	// Space-separated acr_values requested by default. The login page renders a button per auth
	// factor that is the intersection of these requested values and the values the client registered
	// (authContextRefs). KBI is deliberately excluded here so existing scenarios are unaffected; the
	// KBI scenario requests it explicitly via DEFAULT_ACR_VALUES + " " + KBI_ACR_VALUE.
	public static final String DEFAULT_ACR_VALUES = "mosip:idp:acr:generated-code mosip:idp:acr:biometrics mosip:idp:acr:linked-wallet mosip:idp:acr:password";

	/** acr_value that maps to the KBI (Knowledge-Based Identity) login factor in eSignet. */
	public static final String KBI_ACR_VALUE = "mosip:idp:acr:knowledge";

	public static String generateDirectAuthorizeUrl(String clientId) throws SecurityXSSException {
		return generateDirectAuthorizeUrl(clientId, DEFAULT_ACR_VALUES, null);
	}

	/**
	 * Same as {@link #generateDirectAuthorizeUrl(String)} but includes a PKCE S256 challenge.
	 * Required for clients that reject authorize requests without {@code code_challenge}
	 * (this environment's Test-postman client returns {@code invalid_request / code_challenge is
	 * required} otherwise, bouncing to the RP instead of the login page).
	 */
	public static String generateDirectAuthorizeUrlWithPkce(String clientId) throws SecurityXSSException {
		return generateDirectAuthorizeUrlWithPkce(clientId, DEFAULT_ACR_VALUES, null);
	}

	/**
	 * Same as {@link #generateDirectAuthorizeUrl(String, String, String)} but includes a PKCE S256
	 * challenge. Needed when the request's ACR/locale must survive: this environment's Test-postman
	 * client rejects URLs without {@code code_challenge}, and the RP "Sign in with eSignet" recovery
	 * would replace a custom ACR list with the RP's default multi-factor request.
	 */
	public static String generateDirectAuthorizeUrlWithPkce(String clientId, String acrValues, String uiLocales)
			throws SecurityXSSException {
		return buildDirectAuthorizeUrl(clientId, scope, true, acrValues, uiLocales, true);
	}

	public static String generateDirectAuthorizeUrl(String clientId, String acrValues) throws SecurityXSSException {
		return generateDirectAuthorizeUrl(clientId, acrValues, null);
	}

	/** @param uiLocales OIDC ui_locales (e.g. "en"); null to omit, as most callers do. */
	public static String generateDirectAuthorizeUrl(String clientId, String acrValues, String uiLocales)
			throws SecurityXSSException {
		return buildDirectAuthorizeUrl(clientId, scope, true, acrValues, uiLocales);
	}

	public static String generateDirectAuthorizeUrlWithoutPrompt(String clientId) throws SecurityXSSException {
		String url = generateDirectAuthorizeUrl(clientId);
		// OIDC's "don't show consent again, use the existing session silently" signal is an explicit
		// prompt=none - simply omitting the prompt param (previously) leaves the server to pick its own
		// default, which this environment apparently takes as "ask again" rather than "silent" - confirmed
		// live via ConsentRegistry TC_11 (same VID, second login) still landing back on the full consent
		// screen instead of skipping it.
		return url.replace("prompt=" + prompt, "prompt=none");
	}

	public static String buildAuthorizeUrlForClientKey(String clientIdKey)
			throws SecurityXSSException, JsonProcessingException {
		String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		String template = EsignetConfigManager.getproperty("authorizeUrlTemplate");
		String clientId = resolveClientId(clientIdKey);
		if (isParRequired()) {
			String clientAssertion = resolveClientAssertionPlaceholder(clientIdKey);
			String requestUri = generateParRequestUri(clientIdKey, clientAssertion);
			return baseUrl + template.replace("$REQUEST_URI$", requestUri).replace("$CLIENT_ID$", clientId);
		}
		// This environment's clients require PKCE; a URL without code_challenge
		// bounces to invalid_request (same as TC_11's hand-built authorize URL).
		String lang = BaseTestUtil.getThreadLocalLanguage();
		String iso = lang != null ? LanguageUtil.getIsoLanguageCode(lang) : null;
		String uiLocales = iso != null ? iso : "en";
		return generateDirectAuthorizeUrlWithPkce(clientId, DEFAULT_ACR_VALUES, uiLocales);
	}

	private static String resolveClientAssertionPlaceholder(String clientIdKey) {
		if (!ConsentDbUtil.SECONDARY_CLIENT_ID_KEY.equals(clientIdKey)) {
			return "$CLIENT_ASSERTION_PAR_JWT$";
		}
		if (getPreconfiguredSecondaryOidcClientId() != null) {
			return "$CLIENT_ASSERTION_PAR_JWT_SECONDARY$";
		}
		String primary = getPreconfiguredPrimaryOidcClientId();
		try {
			String resolved = AdminTestUtil.replaceIdWithAutogeneratedId(clientIdKey, "$ID:");
			if (primary != null && primary.equals(resolved)) {
				return "$CLIENT_ASSERTION_PAR_JWT$";
			}
		} catch (Exception ignored) {
			if (primary != null) {
				return "$CLIENT_ASSERTION_PAR_JWT$";
			}
		}
		return "$CLIENT_ASSERTION_PAR_JWT_SECONDARY$";
	}

	/**
	 * Rebuilds and navigates to a fresh /authorize URL so the OAuth linked-transaction clock
	 * restarts immediately before authentication (OTP or biometrics). Skipped when the URL was
	 * intentionally tampered for negative InvalidUrl scenarios.
	 */
	public static void refreshOAuthAuthorizeSession(WebDriver driver)
			throws SecurityXSSException, JsonProcessingException {
		if (driver == null || BasePage.authorizeUrlTampered) {
			return;
		}
		String esignetBase = EsignetConfigManager.getproperty("eSignetbaseurl");
		String current = driver.getCurrentUrl();
		if (current == null || esignetBase == null || !current.startsWith(esignetBase)) {
			return;
		}

		String freshUrl = buildFreshAuthorizeUrlFromActiveContext();
		BasePage.authorizeUrl = freshUrl;
		driver.get(freshUrl);
		BasePage.markAuthorizeSessionFresh();
		// PKCE-mandated clients may bounce to the RP with invalid_request - recover via Sign In with eSignet.
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> {
				String url = d.getCurrentUrl();
				return url != null && (url.contains("error=invalid_request") || url.contains("userprofile")
						|| !d.findElements(By.id("sign-in-with-esignet")).isEmpty()
						|| !d.findElements(By.cssSelector("[id^='acr_']")).isEmpty()
						|| !d.findElements(By.id("language_selection")).isEmpty());
			});
		} catch (TimeoutException ignored) {
			// Continue to the main landmark wait below.
		}
		String afterNav = driver.getCurrentUrl();
		if (afterNav != null && (afterNav.contains("error=invalid_request") || afterNav.contains("userprofile"))) {
			// This client requires PKCE (confirmed: every hand-built direct-authorize URL for it bounces
			// here with "code_challenge is required"), so this recovery click is the normal path, not a
			// rare edge case. An instant, zero-wait isDisplayed() check here (previously) missed the
			// button when the RP page was still settling right after the bounce-back - confirmed live
			// (still timing out on the landmark wait below even after the switchToNewWindowIfOpened fix),
			// same root cause as BasePage.clickSignInWithEsignetOnRelyingPartyPortal()'s own locator loop.
			try {
				WebElement signIn = new WebDriverWait(driver, Duration.ofSeconds(10))
						.until(ExpectedConditions.elementToBeClickable(By.id("sign-in-with-esignet")));
				// Like every other "Sign In with eSignet" click in this suite, it can open a new browser
				// tab/window - not switching into it left the driver watching the stale original tab,
				// which never gains any of the landmarks waited for below, timing out after 30s.
				String originalWindow = driver.getWindowHandle();
				java.util.Set<String> windowsBeforeClick = driver.getWindowHandles();
				signIn.click();
				BasePage.switchToNewWindowIfOpened(driver, originalWindow, windowsBeforeClick);
			} catch (TimeoutException ignored) {
				// Continue to the main landmark wait below - it will time out with a clear message.
			}
		}
		// esignet-go stays on /signin (no #<payload> redirect) - wait for login UI landmarks.
		new WebDriverWait(driver, Duration.ofSeconds(30)).until(org.openqa.selenium.support.ui.ExpectedConditions.or(
				ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.cssSelector("[id^='acr_']")),
				ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.id("username_input")),
				ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.id("language_selection")),
				ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.id("navbar-header"))));
		logger.info("Refreshed OAuth authorize session");
	}

	public static String buildFreshAuthorizeUrlFromActiveContext()
			throws SecurityXSSException, JsonProcessingException {
		String clientIdKey = BasePage.authorizeClientIdKey != null ? BasePage.authorizeClientIdKey
				: "$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$";
		if (BasePage.parScenario || isParRequired()) {
			String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
			String template = EsignetConfigManager.getproperty("authorizeUrlTemplate");
			String clientAssertion = BasePage.authorizeClientAssertion != null ? BasePage.authorizeClientAssertion
					: "$CLIENT_ASSERTION_PAR_JWT$";
			String requestUri = generateParRequestUri(clientIdKey, clientAssertion);
			String clientId = resolveClientId(clientIdKey);
			return baseUrl + template.replace("$REQUEST_URI$", requestUri).replace("$CLIENT_ID$", clientId);
		}
		String clientId = resolveClientId(clientIdKey);
		if (BasePage.authorizeScopeOnlyScenario) {
			return generateDirectAuthorizeUrlWithoutClaims(clientId, AUTHORIZE_SCOPE_ONLY);
		}
		String acrValues = BasePage.authorizeAcrValues != null ? BasePage.authorizeAcrValues : DEFAULT_ACR_VALUES;
		String uiLocales = BasePage.authorizeUiLocales;
		if (BasePage.authorizeRequiresPkce) {
			return generateDirectAuthorizeUrlWithPkce(clientId, acrValues, uiLocales);
		}
		return generateDirectAuthorizeUrl(clientId, acrValues, uiLocales);
	}

	public static String generateDirectAuthorizeUrlWithoutClaims(String clientId, String customScope)
			throws SecurityXSSException {
		// PKCE is required by this environment's OIDC client (hand-built URLs bounce with
		// invalid_request / "code_challenge is required"). Other scenarios recover via the RP
		// "Sign in with eSignet" button, which would replace this no-claims/Manage-VID request
		// with the RP's default claims flow - so this URL must carry PKCE itself.
		return buildDirectAuthorizeUrl(clientId, customScope, false, DEFAULT_ACR_VALUES, null, true);
	}

	private static String buildDirectAuthorizeUrl(String clientId, String requestedScope, boolean includeClaims,
			String acrValues, String uiLocales) throws SecurityXSSException {
		return buildDirectAuthorizeUrl(clientId, requestedScope, includeClaims, acrValues, uiLocales, false);
	}

	private static String buildDirectAuthorizeUrl(String clientId, String requestedScope, boolean includeClaims,
			String acrValues, String uiLocales, boolean includePkce) throws SecurityXSSException {

		String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		String redirectUri = EsignetConfigManager.getproperty("baseurl") + "userprofile";
		String nonce = String.valueOf(Calendar.getInstance().getTimeInMillis());

		Charset utf8 = StandardCharsets.UTF_8;
		StringBuilder url = new StringBuilder(
				baseUrl + EsignetConfigManager.getProperty("esignetAuthorizeEndpoint", "/authorize") + "?");
		url.append("client_id=").append(URLEncoder.encode(clientId, utf8));
		url.append("&response_type=").append(responseType);
		url.append("&scope=").append(URLEncoder.encode(requestedScope, utf8).replace("+", "%20"));
		url.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, utf8));
		url.append("&display=").append(display);
		url.append("&prompt=").append(prompt);
		url.append("&nonce=").append(nonce);
		url.append("&state=").append(state);
		url.append("&acr_values=").append(URLEncoder.encode(acrValues, utf8).replace("+", "%20"));
		if (includeClaims) {
			org.json.simple.JSONObject claimRequest = getClaimsJsonSafely();
			url.append("&claims=").append(URLEncoder.encode(claimRequest.toString(), utf8));
		}
		if (uiLocales != null && !uiLocales.isBlank()) {
			url.append("&ui_locales=").append(URLEncoder.encode(uiLocales, utf8));
		}
		if (includePkce) {
			url.append("&code_challenge=").append(URLEncoder.encode(generatePkceS256Challenge(), utf8));
			url.append("&code_challenge_method=S256");
		}

		return url.toString();
	}

	private static String generatePkceS256Challenge() {
		try {
			byte[] verifierBytes = new byte[32];
			new SecureRandom().nextBytes(verifierBytes);
			String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
			byte[] challengeBytes = MessageDigest.getInstance("SHA-256")
					.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to generate PKCE S256 code_challenge", e);
		}
	}

	// Raw actuator string, cached separately from the derived `pluginName` (mock/mosipid) to avoid
	// the two colliding.
	private static String serverAuthenticatorPluginName = null;

	private static Boolean esignetActuatorEnabled = null;

	/** Single boolean-config-flag convention for this file: only "true" (case-insensitive) is truthy. */
	private static boolean parseConfiguredBoolean(String key, boolean defaultWhenBlank) {
		String value = EsignetConfigManager.getProperty(key, "").trim();
		return value.isEmpty() ? defaultWhenBlank : Boolean.parseBoolean(value);
	}

	// Single gate in front of every eSignet actuator/env call in this module (getPluginName()'s own
	// fallback, isSunbirdAuthenticatorActive(), and any other caller of
	// getIdentityPluginNameFromEsignetActuator()). Set esignetActuatorEnabled=false in config for
	// deployments (e.g. the Thunder/eSignet-go build) that don't expose a Spring actuator at all -
	// actuator/env 404s there on every call, so there's nothing useful to retry.
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

		org.json.simple.JSONObject claimRequest = getClaimsJsonSafely();
		JSONObject requestBody = new JSONObject();

		requestBody.put("display", display);
		requestBody.put("response_type", responseType);
		requestBody.put("client_id", resolveClientId("$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$"));
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

	/**
	 * Builds an authorize URL without {@code nonce} and {@code state}, matching the InvalidUrl
	 * scenario's "remove nonce and state" step. Uses the direct flow when PAR is not mandated so a
	 * preconfigured {@code oidcClientId} works without a matching PAR client assertion JWK.
	 * Always includes PKCE - without it this client bounces to the RP with
	 * {@code code_challenge is required}, which is not a nonce/state failure (confirmed live:
	 * the same URL with a dummy S256 challenge lands on /signin whether nonce/state are present
	 * or not).
	 */
	public static String generateAuthorizeUrlWithoutNonceAndState()
			throws SecurityXSSException, JsonProcessingException {
		String clientIdKey = "$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$";
		String clientId = resolveClientId(clientIdKey);
		if (isParRequired()) {
			String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
			String template = EsignetConfigManager.getproperty("authorizeUrlTemplate");
			String requestUri = generateParRequestWithoutNonceAndState();
			return baseUrl + template.replace("$REQUEST_URI$", requestUri).replace("$CLIENT_ID$", clientId);
		}
		return removeQueryParams(generateDirectAuthorizeUrlWithPkce(clientId), "nonce", "state");
	}

	public static String removeQueryParams(String url, String... paramNames) {
		if (url == null || paramNames == null || paramNames.length == 0) {
			return url;
		}
		java.util.Set<String> toRemove = java.util.Set.of(paramNames);
		int queryStart = url.indexOf('?');
		if (queryStart < 0) {
			return url;
		}
		String base = url.substring(0, queryStart);
		String query = url.substring(queryStart + 1);
		String fragment = "";
		int hashIndex = query.indexOf('#');
		if (hashIndex >= 0) {
			fragment = query.substring(hashIndex);
			query = query.substring(0, hashIndex);
		}
		String rebuilt = java.util.Arrays.stream(query.split("&"))
				.filter(part -> !part.isBlank())
				.filter(part -> {
					int eq = part.indexOf('=');
					String name = eq >= 0 ? part.substring(0, eq) : part;
					return !toRemove.contains(name);
				})
				.reduce((a, b) -> a + "&" + b)
				.orElse("");
		if (rebuilt.isEmpty()) {
			return base + fragment;
		}
		return base + "?" + rebuilt + fragment;
	}

	/* ======================= DYNAMIC MOCK IDENTITY REQUEST GENERATION =======================
	 * Thin wrapper around AdminTestUtil.generateDynamicRequestFromSchema (apitest-commons),
	 * which is the shared, schema-agnostic engine also used by esignet/api-test's EsignetUtil
	 * and esignet-signup/api-test's SignupUtil. Only the schema-endpoint lookup and the
	 * module-specific value defaults (mockIdentityValueMapping.properties) live here.
	 */

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
		return AdminTestUtil.generateDynamicRequestFromSchema(schemaStr, testCaseName, MOCK_IDENTITY_VALUE_MAP);
	}

	// Overrides (rather than delegates to) AdminTestUtil#extractAndStoreIdentityDetailsFromRequest -
	// that inherited version also extracts and persists "password" via writeAutoGeneratedId(...,
	// "PASSWORD", ...), but nothing in this suite consumes a PASSWORD generated-ID key, so persisting a
	// plaintext password to the generated-ID properties file is unnecessary risk. individualId/email/
	// phone extraction is unchanged.
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