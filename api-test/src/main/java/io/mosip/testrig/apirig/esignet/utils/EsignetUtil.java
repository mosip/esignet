package io.mosip.testrig.apirig.esignet.utils;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.mosip.testrig.apirig.dataprovider.BiometricDataProvider;
import io.mosip.testrig.apirig.dbaccess.DBManager;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.esignet.testrunner.MosipTestRunner;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.CertsUtil;
import io.mosip.testrig.apirig.utils.EncryptionDecrptionUtil;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.GlobalMethods;
import io.mosip.testrig.apirig.utils.JWKKeyUtil;
import io.mosip.testrig.apirig.utils.KeycloakUserManager;
import io.mosip.testrig.apirig.utils.RestClient;
import io.mosip.testrig.apirig.utils.SkipTestCaseHandler;
//import io.restassured.RestAssured;
import io.restassured.response.Response;

public class EsignetUtil extends AdminTestUtil {

	private static final Logger logger = Logger.getLogger(EsignetUtil.class);
	public static String pluginName = null;
	private static Faker faker = new Faker();
	private static String fullNameForSunBirdR = generateFullNameForSunBirdR();
	private static String dobForSunBirdR = generateDobForSunBirdR();
	private static String policyNumberForSunBirdR = generateRandomNumberString(9);
	
	public static List<String> testCasesInRunScope = new ArrayList<>();
	
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}
	
	public static void dBCleanup() {
		DBManager.executeDBQueries(EsignetConfigManager.getKMDbUrl(), EsignetConfigManager.getKMDbUser(),
				EsignetConfigManager.getKMDbPass(), EsignetConfigManager.getKMDbSchema(),
				getGlobalResourcePath() + "/" + "config/keyManagerDataDeleteQueriesForEsignet.txt");

		DBManager.executeDBQueries(EsignetConfigManager.getIdaDbUrl(), EsignetConfigManager.getIdaDbUser(),
				EsignetConfigManager.getPMSDbPass(), EsignetConfigManager.getIdaDbSchema(),
				getGlobalResourcePath() + "/" + "config/idaDeleteQueriesForEsignet.txt");

		DBManager.executeDBQueries(EsignetConfigManager.getMASTERDbUrl(), EsignetConfigManager.getMasterDbUser(),
				EsignetConfigManager.getMasterDbPass(), EsignetConfigManager.getMasterDbSchema(),
				getGlobalResourcePath() + "/" + "config/masterDataDeleteQueriesForEsignet.txt");

		DBManager.executeDBQueries(EsignetConfigManager.getPMSDbUrl(), EsignetConfigManager.getPMSDbUser(),
				EsignetConfigManager.getPMSDbPass(), EsignetConfigManager.getPMSDbSchema(),
				getGlobalResourcePath() + "/" + "config/pmsDataDeleteQueries.txt");
	}
	
	public static void getSupportedLanguage() {

		if (EsignetConfigManager.getproperty("esignetSupportedLanguage") != null) {
			BaseTestCase.languageList.add(EsignetConfigManager.getproperty(EsignetConstants.ESIGNET_SUPPORTED_LANGUAGE));
			logger.info("Supported Language = " + EsignetConfigManager.getproperty(EsignetConstants.ESIGNET_SUPPORTED_LANGUAGE));
		} else {
			logger.error("Language not found");
		}
	}
	
	public static JSONArray esignetActiveProfiles = null;
	
	public static String getIdentityPluginNameFromEsignetActuator() {
		// Possible values = IdaAuthenticatorImpl, MockAuthenticationService, SunbirdRCAuthenticationService
		if (pluginName != null && !pluginName.isBlank()) {
			return pluginName;
		}
		pluginName = getValueFromEsignetActuator(EsignetConstants.CLASS_PATH_APPLICATION_PROPERTIES,
				"mosip.esignet.integration.authenticator");

		return pluginName;
	}
	public static String getPluginName() {
		try {
			String pluginServiceName = EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase();
		    if (pluginServiceName.contains("mockauthenticationservice")) {
		        return "mock";
		    } else if (pluginServiceName.contains("sunbirdrcauthenticationservice")) {
		        return "sunbirdrc";
		    } else {
		        return "mosip-id";
		    }
		} catch(Exception e) {
			logger.error("Failed to get plugin name from actuator", e);			
		}
		return "null";
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

	public static String isTestCaseValidForExecution(TestCaseDTO testCaseDTO) {
		String testCaseName = testCaseDTO.getTestCaseName();
		currentTestCaseName = testCaseName;
		
		int indexof = testCaseName.indexOf("_");
		String modifiedTestCaseName = testCaseName.substring(indexof + 1);

		addTestCaseDetailsToMap(modifiedTestCaseName, testCaseDTO.getUniqueIdentifier());
		
		if (!testCasesInRunScope.isEmpty()
				&& testCasesInRunScope.contains(testCaseDTO.getUniqueIdentifier()) == false) {
			throw new SkipException(GlobalConstants.NOT_IN_RUN_SCOPE_MESSAGE);
		}
		
		//When the captcha is enabled we cannot execute the test case as we can not generate the captcha token
		if (isCaptchaEnabled() == true) {
			GlobalMethods.reportCaptchaStatus(GlobalConstants.CAPTCHA_ENABLED, true);
			throw new SkipException(GlobalConstants.CAPTCHA_ENABLED_MESSAGE);
		}
		
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
					|| testCaseName.contains("ESignet_AuthenticateUserIDP_NonAuth_uin_Otp_Valid_Smoke")
					|| testCaseName.contains("ESignet_UpdateOIDCClient_StatusCode_Diff_Token_Neg")
					|| testCaseName.contains("ESignet_CreateOIDCClient_StatusCode_Diff_Token_Neg"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}
			if ((testCaseName.contains("_UpdateOIDCClientV3_MOSIPID_")
					|| testCaseName.contains("_OAuthDetailsRequest_V3_MOSIPID_")
					|| testCaseName.contains("_AuthenticateUser_V3_MOSIPID_")
					|| testCaseName.contains("_AuthorizationCode_MOSIPID_")
					|| testCaseName.contains("_GenerateToken_MOSIPID_")
					|| testCaseName.contains("_GetOidcUserInfo_MOSIPID_"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

		} else if (getIdentityPluginNameFromEsignetActuator().toLowerCase().contains("idaauthenticatorimpl")) {
			// Let run test cases eSignet & MOSIP API calls --- both UIN and VID

//			BaseTestCase.setSupportedIdTypes(Arrays.asList("UIN", "VID"));
			getSupportedIdTypesValueFromActuator();
			
			logger.info("supportedIdType = " + supportedIdType);

			String endpoint = testCaseDTO.getEndPoint();
			if (endpoint.contains("/v1/signup/") == true || endpoint.contains("/mock-identity-system/") == true
					|| ((testCaseName.equals("ESignet_CreateOIDCClient_all_Valid_Smoke_sid")
							|| testCaseName.equals("ESignet_CreateOIDCClient_Misp_Valid_Smoke_sid")
							|| testCaseName.equals("ESignet_CreateOIDCClient_NonAuth_all_Valid_Smoke_sid"))
							&& endpoint.contains("/v1/esignet/client-mgmt/oauth-client"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}
			
//			if ((testCaseName.contains("_CreateOIDCClientV3_") || testCaseName.contains("_UpdateOIDCClientV3_"))) {
//				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
//			}
			
			if ((testCaseName.contains("_CreateOIDCClientV3_MOCK_")
					|| testCaseName.contains("_UpdateOIDCClientV3_MOCK_")
					|| testCaseName.contains("_OAuthDetailsRequest_V3_MOCK_")
					|| testCaseName.contains("_AuthenticateUser_V3_MOCK_")
					|| testCaseName.contains("_AuthorizationCode_MOCK_")
					|| testCaseName.contains("_GenerateToken_MOCK_")
					|| testCaseName.contains("_GetOidcUserInfo_MOCK_"))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}
			 

			JSONArray individualBiometricsArray = new JSONArray(
					getValueFromAuthActuator("json-property", "individualBiometrics"));
			String individualBiometrics = individualBiometricsArray.getString(0);

			if ((testCaseName.contains("_KycBioAuth_") || testCaseName.contains("_BioAuth_")
					|| testCaseName.contains("_SendBindingOtp_uin_Email_Valid_Smoke"))
					&& (!isElementPresent(globalRequiredFields, individualBiometrics))) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}

		} else if (getIdentityPluginNameFromEsignetActuator().toLowerCase().contains("sunbirdrcauthenticationservice")) {
			// Let run test cases eSignet & Sunbird (for identity)   -- only KBI 
			
//			String endpoint = testCaseDTO.getEndPoint();
			
			if (testCaseName.contains("SunBird") == false) {
				throw new SkipException(GlobalConstants.FEATURE_NOT_SUPPORTED_MESSAGE);
			}
			
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
		if (jsonString.contains("$ID:")) {
			jsonString = replaceIdWithAutogeneratedId(jsonString, "$ID:");
		}
		
		if (jsonString.contains(GlobalConstants.TIMESTAMP)) {
			jsonString = replaceKeywordValue(jsonString, GlobalConstants.TIMESTAMP, generateCurrentUTCTimeStamp());
		}
		
		if (jsonString.contains("$SUNBIRD_SCOPE$")) {
			jsonString = replaceKeywordValue(jsonString, "$SUNBIRD_SCOPE$",
					getValueFromEsignetActuator(jsonString,
							EsignetConstants.MOSIP_ESIGNET_SUPPORTED_CREDENTIAL_SCOPES_LANGUAGE).replace("{'", "")
							.replace("'}", ""));
		}
		
		if (testCaseName.contains("ESignet_GenerateApiKey_")) {
			KeycloakUserManager.createKeyCloakUsers(genPartnerName, genPartnerEmail, "AUTH_PARTNER");
		}
		
		if (testCaseName.contains("ESignet_GenerateApiKeyKyc_")) {
			KeycloakUserManager.createKeyCloakUsers(genPartnerName + "2n", "12d" + genPartnerEmail, "AUTH_PARTNER");
		}
		
		if (jsonString.contains("$THUMBPRINT$")) {
			jsonString = replaceKeywordValue(jsonString, "$THUMBPRINT$", EncryptionDecrptionUtil.idaFirThumbPrint);
		}
		
		if (jsonString.contains("$POLICYNUMBERFORSUNBIRDRC$")) {
			jsonString = replaceKeywordValue(jsonString, "$POLICYNUMBERFORSUNBIRDRC$", policyNumberForSunBirdR);
		}
		
		if (jsonString.contains("$FULLNAMEFORSUNBIRDRC$")) {
			jsonString = replaceKeywordValue(jsonString, "$FULLNAMEFORSUNBIRDRC$", fullNameForSunBirdR);
		}
		
		if (jsonString.contains("$DOBFORSUNBIRDRC$")) {
			jsonString = replaceKeywordValue(jsonString, "$DOBFORSUNBIRDRC$", dobForSunBirdR);
		}
		
		if (jsonString.contains("$CHALLENGEVALUEFORSUNBIRDC$")) {

			HashMap<String, String> mapForChallenge = new HashMap<String, String>();
			mapForChallenge.put(GlobalConstants.FULLNAME, fullNameForSunBirdR);
			mapForChallenge.put(GlobalConstants.DOB, dobForSunBirdR);

			String challenge = gson.toJson(mapForChallenge);

			String challengeValue = BiometricDataProvider.toBase64Url(challenge);

			jsonString = replaceKeywordValue(jsonString, "$CHALLENGEVALUEFORSUNBIRDC$", challengeValue);
		}
		
		if (jsonString.contains("$UNIQUENONCEVALUEFORESIGNET$")) {
			jsonString = replaceKeywordValue(jsonString, "$UNIQUENONCEVALUEFORESIGNET$",
					String.valueOf(Calendar.getInstance().getTimeInMillis()));
		}
		
		if (jsonString.contains("$ENCRYPTEDSESSIONKEY$")) {
			jsonString = replaceKeywordValue(jsonString, "$ENCRYPTEDSESSIONKEY$", encryptedSessionKeyString);
		}
		
		if (jsonString.contains("$RANDOMIDFOROIDCCLIENT$")) {
			jsonString = replaceKeywordValue(jsonString, "$RANDOMIDFOROIDCCLIENT$",
					"mosip" + generateRandomNumberString(2) + Calendar.getInstance().getTimeInMillis());
		}
		
		if (jsonString.contains("$IDPREDIRECTURI$")) {
			jsonString = replaceKeywordValue(jsonString, "$IDPREDIRECTURI$",
					ApplnURI.replace(GlobalConstants.API_INTERNAL, "healthservices") + "/userprofile");
		}
		
		if (jsonString.contains("$BINDINGJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen3()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGJWK1);

				settriggerESignetKeyGen3(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGJWK1);

			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGJWKKEYVID$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen4()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGJWKVID);
				settriggerESignetKeyGen4(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGJWKVID);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGJWKKEYVID$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen5()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTJWK);
				settriggerESignetKeyGen5(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTJWKKEYVID$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen6()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTJWKVID);
				settriggerESignetKeyGen6(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTJWKVID);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTJWKKEYVID$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTSAMECLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen7()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTSAMECLAIMJWK);
				settriggerESignetKeyGen7(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTSAMECLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTSAMECLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTSAMECLAIMVIDJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen8()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTVIDSAMECLAIMJWK);
				settriggerESignetKeyGen8(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTVIDSAMECLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTSAMECLAIMVIDJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTEMPTYCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen9()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTEMPTYCLAIMJWK);
				settriggerESignetKeyGen9(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTEMPTYCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTEMPTYCLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTUSER2JWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen10()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTUSER2JWK);
				settriggerESignetKeyGen10(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTUSER2JWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTUSER2JWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTVIDUSER2JWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen11()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTVIDUSER2JWK);
				settriggerESignetKeyGen11(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTVIDUSER2JWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTVIDUSER2JWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTACCEPTEDCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen17()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTACCEPTEDCLAIMJWK);
				settriggerESignetKeyGen17(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTACCEPTEDCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTACCEPTEDCLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTFORACCEPTEDCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen18()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTFORACCEPTEDCLAIMJWK);
				settriggerESignetKeyGen18(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTFORACCEPTEDCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTFORACCEPTEDCLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTFOROPTIONALCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen19()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTFOROPTIONALCLAIMJWK);
				settriggerESignetKeyGen19(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTFOROPTIONALCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTFOROPTIONALCLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTFORPARTIALESSENTIALOPTIONALCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen20()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTFORPARTIALESSENTIALOPTIONALCLAIMJWK);
				settriggerESignetKeyGen20(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTFORPARTIALESSENTIALOPTIONALCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTFORPARTIALESSENTIALOPTIONALCLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTFORONLYESSENTIALCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen21()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTFORONLYESSENTIALCLAIMJWK);
				settriggerESignetKeyGen21(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTFORONLYESSENTIALCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTFORONLYESSENTIALCLAIMJWKKEY$",
					jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTFORNULLESSENTIALCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen22()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTFORNULLESSENTIALCLAIMJWK);
				settriggerESignetKeyGen22(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTFORNULLESSENTIALCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTFORNULLESSENTIALCLAIMJWKKEY$",
					jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTFOROPTIONALACCEPTEDCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen23()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTFOROPTIONALACCEPTEDCLAIMJWK);
				settriggerESignetKeyGen23(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTFOROPTIONALACCEPTEDCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTFOROPTIONALACCEPTEDCLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGCONSENTFORNULLACCEPTEDCLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen24()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGCONSENTFORNULLACCEPTEDCLAIMJWK);
				settriggerESignetKeyGen24(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGCONSENTFORNULLACCEPTEDCLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGCONSENTFORNULLACCEPTEDCLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGFOROPTIONALUSER1CLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen25()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGFOROPTIONALUSER1CLAIMJWK);
				settriggerESignetKeyGen25(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGFOROPTIONALUSER1CLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGFOROPTIONALUSER1CLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGFORPARTIALESSENTIALOPTIONALUSER1CLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen26()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGFORPARTIALESSENTIALOPTIONALUSER1CLAIMJWK);
				settriggerESignetKeyGen26(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGFORPARTIALESSENTIALOPTIONALUSER1CLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGFORPARTIALESSENTIALOPTIONALUSER1CLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGFORONLYESSENTIALUSER1CLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen27()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGFORONLYESSENTIALUSER1CLAIMJWK);
				settriggerESignetKeyGen27(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGFORONLYESSENTIALUSER1CLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGFORONLYESSENTIALUSER1CLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGFORNULLESSENTIALUSER1CLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen28()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGFORNULLESSENTIALUSER1CLAIMJWK);
				settriggerESignetKeyGen28(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGFORNULLESSENTIALUSER1CLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGFORNULLESSENTIALUSER1CLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGFOROPTIONALACCEPTEDUSER1CLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen29()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGFOROPTIONALACCEPTEDUSER1CLAIMJWK);
				settriggerESignetKeyGen29(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGFOROPTIONALACCEPTEDUSER1CLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGFOROPTIONALACCEPTEDUSER1CLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$BINDINGFORNULLACCEPTEDUSER1CLAIMJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen30()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(BINDINGFORNULLACCEPTEDUSER1CLAIMJWK);
				settriggerESignetKeyGen30(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(BINDINGFORNULLACCEPTEDUSER1CLAIMJWK);
			}
			jsonString = replaceKeywordValue(jsonString, "$BINDINGFORNULLACCEPTEDUSER1CLAIMJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$OIDCJWKKEY$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen1()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK1);
				settriggerESignetKeyGen1(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK1);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY$", jwkKey);
		}
		
		if (jsonString.contains("$OIDCJWKKEY2$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen2()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK2);
				settriggerESignetKeyGen2(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK2);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY2$", jwkKey);
		}
		
		if (jsonString.contains("$OIDCJWKKEY3$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen12()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK3);
				settriggerESignetKeyGen12(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK3);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY3$", jwkKey);
		}
		
		if (jsonString.contains("$OIDCJWKKEY4$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen13()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK4);
				settriggerESignetKeyGen13(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK4);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY4$", jwkKey);
		}
		
		if (jsonString.contains("$OIDCJWKKEY5$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen15()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK5);
				settriggerESignetKeyGen15(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK5);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY5$", jwkKey);
		}
		if (jsonString.contains("$OIDCJWKKEY6$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen16()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK6);
				settriggerESignetKeyGen16(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK6);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY6$", jwkKey);
		}
		if (jsonString.contains("$OIDCJWKKEY7$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen31()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK7);
				settriggerESignetKeyGen31(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK7);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY7$", jwkKey);
		}
		if (jsonString.contains("$OIDCJWKKEY8$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen32()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK8);
				settriggerESignetKeyGen32(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK8);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY8$", jwkKey);
		}
		if (jsonString.contains("$OIDCJWKKEY9$")) {
			String jwkKey = "";
			if (gettriggerESignetKeyGen33()) {
				jwkKey = JWKKeyUtil.generateAndCacheJWKKey(OIDCJWK9);
				settriggerESignetKeyGen33(false);
			} else {
				jwkKey = JWKKeyUtil.getJWKKey(OIDCJWK9);
			}
			jsonString = replaceKeywordValue(jsonString, "$OIDCJWKKEY9$", jwkKey);
		}
		
		if (jsonString.contains("$CLIENT_ASSERTION_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK1);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey1 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey1 =" + oidcJWKKey1);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			
			String tempUrl = getValueFromEsignetWellKnownEndPoint("token_endpoint", EsignetConfigManager.getEsignetBaseUrl());
			
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_JWK$",
					signJWKKey(clientId, oidcJWKKey1, tempUrl));
		}
		
		if (jsonString.contains("$CLIENT_ASSERTION_USER3_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK3);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey3 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey3 =" + oidcJWKKey3);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			
			String tempUrl = getValueFromEsignetWellKnownEndPoint("token_endpoint", EsignetConfigManager.getEsignetBaseUrl());
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_USER3_JWK$",
					signJWKKey(clientId, oidcJWKKey3, tempUrl));
		}
		
		if (jsonString.contains("$CLIENT_ASSERTION_USER4_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK4);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey4 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey4 =" + oidcJWKKey4);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_USER4_JWK$",
					signJWKKeyForMock(clientId, oidcJWKKey4));
		}
		
		if (jsonString.contains("$CLIENT_ASSERTION_USER5_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK5);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey5 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey5 =" + oidcJWKKey5);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_USER5_JWK$",
					signJWKKeyForMock(clientId, oidcJWKKey5));
		}
		if (jsonString.contains("$CLIENT_ASSERTION_USER6_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK6);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey6 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey6 =" + oidcJWKKey6);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_USER6_JWK$",
					signJWKKeyForMock(clientId, oidcJWKKey6));
		}
		if (jsonString.contains("$CLIENT_ASSERTION_USER7_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK7);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey7 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey7 =" + oidcJWKKey7);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_USER7_JWK$",
					signJWKKeyForMock(clientId, oidcJWKKey7));
		}
		if (jsonString.contains("$CLIENT_ASSERTION_USER8_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK8);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey8 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey8 =" + oidcJWKKey8);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_USER8_JWK$",
					signJWKKeyForMock(clientId, oidcJWKKey8));
		}
		if (jsonString.contains("$CLIENT_ASSERTION_USER9_JWK$")) {
			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK9);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey9 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey9 =" + oidcJWKKey9);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}
			JSONObject request = new JSONObject(jsonString);
			String clientId = null;
			if (request.has("client_id")) {
				clientId = request.get("client_id").toString();
			}
			jsonString = replaceKeywordValue(jsonString, "$CLIENT_ASSERTION_USER9_JWK$",
					signJWKKeyForMock(clientId, oidcJWKKey9));
		}
		
		if (jsonString.contains("$WLATOKEN$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKEN$",
					generateWLAToken(jsonString, BINDINGJWK1, BINDINGCERTFILE));
		}
		
		if (jsonString.contains("$WLATOKENVID$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENVID$",
					generateWLAToken(jsonString, BINDINGJWKVID, BINDINGCERTFILEVID));
		}
		
		if (jsonString.contains("$WLATOKENCONSENT$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENT$",
					generateWLAToken(jsonString, BINDINGCONSENTJWK, BINDINGCERTCONSENTFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATURE$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATURE$",
					generateDetachedSignature(jsonString, BINDINGCONSENTJWK, BINDINGCERTCONSENTFILE));
		}
		
		if (jsonString.contains("$WLATOKENCONSENTVID$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENTVID$",
					generateWLAToken(jsonString, BINDINGCONSENTJWKVID, BINDINGCERTCONSENTVIDFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREVID$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREVID$",
					generateDetachedSignature(jsonString, BINDINGCONSENTJWKVID, BINDINGCERTCONSENTVIDFILE));
		}
		
		if (jsonString.contains("$WLATOKENCONSENTSAMECLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENTSAMECLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTSAMECLAIMJWK, BINDINGCERTCONSENTSAMECLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATURESAMECLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATURESAMECLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTSAMECLAIMJWK, BINDINGCERTCONSENTSAMECLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENCONSENTVIDSAMECLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENTVIDSAMECLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTVIDSAMECLAIMJWK, BINDINGCERTCONSENTVIDSAMECLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREVIDSAMECLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREVIDSAMECLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTVIDSAMECLAIMJWK,
							BINDINGCERTCONSENTVIDSAMECLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENCONSENTEMPTYCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENTEMPTYCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTEMPTYCLAIMJWK, BINDINGCERTCONSENTEMPTYCLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENCONSENTUSER2$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENTUSER2$",
					generateWLAToken(jsonString, BINDINGCONSENTUSER2JWK, BINDINGCERTCONSENTUSER2FILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREUSER2$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREUSER2$",
					generateDetachedSignature(jsonString, BINDINGCONSENTUSER2JWK, BINDINGCERTCONSENTUSER2FILE));
		}
		
		if (jsonString.contains("$WLATOKENCONSENTVIDUSER2$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENTVIDUSER2$",
					generateWLAToken(jsonString, BINDINGCONSENTVIDUSER2JWK, BINDINGCERTVIDCONSENTUSER2FILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREVIDUSER2$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREVIDUSER2$",
					generateDetachedSignature(jsonString, BINDINGCONSENTVIDUSER2JWK, BINDINGCERTVIDCONSENTUSER2FILE));
		}
		
		if (jsonString.contains("$WLATOKENCONSENTACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENCONSENTACCEPTEDCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTACCEPTEDCLAIMJWK, BINDINGCERTCONSENTACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREACCEPTEDCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTACCEPTEDCLAIMJWK, BINDINGCERTCONSENTACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFORACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORACCEPTEDCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTFORACCEPTEDCLAIMJWK, BINDINGCERTCONSENTFORACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORACCEPTEDCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTFORACCEPTEDCLAIMJWK, BINDINGCERTCONSENTFORACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFOROPTIONALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFOROPTIONALCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTFOROPTIONALCLAIMJWK, BINDINGCERTCONSENTFOROPTIONALCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFOROPTIONALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFOROPTIONALCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTFOROPTIONALCLAIMJWK, BINDINGCERTCONSENTFOROPTIONALCLAIMFILE));
		}
				
		if (jsonString.contains("$WLATOKENFORPARTIALESSENTIALOPTIONALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORPARTIALESSENTIALOPTIONALCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTFORPARTIALESSENTIALOPTIONALCLAIMJWK, BINDINGCERTCONSENTFORPARTIALESSENTIALOPTIONALCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORPARTIALESSENTIALOPTIONALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORPARTIALESSENTIALOPTIONALCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTFORPARTIALESSENTIALOPTIONALCLAIMJWK, BINDINGCERTCONSENTFORPARTIALESSENTIALOPTIONALCLAIMFILE));
		}
				
		if (jsonString.contains("$WLATOKENFORONLYESSENTIALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORONLYESSENTIALCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTFORONLYESSENTIALCLAIMJWK, BINDINGCERTCONSENTFORONLYESSENTIALCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORONLYESSENTIALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORONLYESSENTIALCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTFORONLYESSENTIALCLAIMJWK, BINDINGCERTCONSENTFORONLYESSENTIALCLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFORNULLESSENTIALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORNULLESSENTIALCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTFORNULLESSENTIALCLAIMJWK, BINDINGCERTCONSENTFORNULLESSENTIALCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORNULLESSENTIALCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORNULLESSENTIALCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTFORNULLESSENTIALCLAIMJWK, BINDINGCERTCONSENTFORNULLESSENTIALCLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFOROPTIONALACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFOROPTIONALACCEPTEDCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTFOROPTIONALACCEPTEDCLAIMJWK, BINDINGCERTCONSENTFOROPTIONALACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFOROPTIONALACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFOROPTIONALACCEPTEDCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTFOROPTIONALACCEPTEDCLAIMJWK, BINDINGCERTCONSENTFOROPTIONALACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFORNULLACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORNULLACCEPTEDCLAIM$",
					generateWLAToken(jsonString, BINDINGCONSENTFORNULLACCEPTEDCLAIMJWK, BINDINGCERTCONSENTFORNULLACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORNULLACCEPTEDCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORNULLACCEPTEDCLAIM$",
					generateDetachedSignature(jsonString, BINDINGCONSENTFORNULLACCEPTEDCLAIMJWK, BINDINGCERTCONSENTFORNULLACCEPTEDCLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFOROPTIONALUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFOROPTIONALUSER1CLAIM$",
					generateWLAToken(jsonString, BINDINGFOROPTIONALUSER1CLAIMJWK, BINDINGCERTFOROPTIONALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFOROPTIONALUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFOROPTIONALUSER1CLAIM$",
					generateDetachedSignature(jsonString, BINDINGFOROPTIONALUSER1CLAIMJWK, BINDINGCERTFOROPTIONALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFORPARTIALESSENTIALOPTIONALUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORPARTIALESSENTIALOPTIONALUSER1CLAIM$",
					generateWLAToken(jsonString, BINDINGFORPARTIALESSENTIALOPTIONALUSER1CLAIMJWK, BINDINGCERTFORPARTIALESSENTIALOPTIONALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORPARTIALESSENTIALOPTIONALUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORPARTIALESSENTIALOPTIONALUSER1CLAIM$",
					generateDetachedSignature(jsonString, BINDINGFORPARTIALESSENTIALOPTIONALUSER1CLAIMJWK, BINDINGCERTFORPARTIALESSENTIALOPTIONALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFORONLYESSENTIALUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORONLYESSENTIALUSER1CLAIM$",
					generateWLAToken(jsonString, BINDINGFORONLYESSENTIALUSER1CLAIMJWK, BINDINGCERTFORONLYESSENTIALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORONLYESSENTIAUSER1LCLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORONLYESSENTIAUSER1LCLAIM$",
					generateDetachedSignature(jsonString, BINDINGFORONLYESSENTIALUSER1CLAIMJWK, BINDINGCERTFORONLYESSENTIALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFORNULLESSENTIALUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORNULLESSENTIALUSER1CLAIM$",
					generateWLAToken(jsonString, BINDINGFORNULLESSENTIALUSER1CLAIMJWK, BINDINGCERTFORNULLESSENTIALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORNULLESSENTIALUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORNULLESSENTIALUSER1CLAIM$",
					generateDetachedSignature(jsonString, BINDINGFORNULLESSENTIALUSER1CLAIMJWK, BINDINGCERTFORNULLESSENTIALUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFOROPTIONALACCEPTEDUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFOROPTIONALACCEPTEDUSER1CLAIM$",
					generateWLAToken(jsonString, BINDINGFOROPTIONALACCEPTEDUSER1CLAIMJWK, BINDINGCERTFOROPTIONALACCEPTEDUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFOROPTIONALACCEPTEDUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFOROPTIONALACCEPTEDUSER1CLAIM$",
					generateDetachedSignature(jsonString, BINDINGFOROPTIONALACCEPTEDUSER1CLAIMJWK, BINDINGCERTFOROPTIONALACCEPTEDUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$WLATOKENFORNULLACCEPTEDUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$WLATOKENFORNULLACCEPTEDUSER1CLAIM$",
					generateWLAToken(jsonString, BINDINGFORNULLACCEPTEDUSER1CLAIMJWK, BINDINGCERTFORNULLACCEPTEDUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$CONSENTDETACHEDSIGNATUREFORNULLACCEPTEDUSER1CLAIM$")) {
			jsonString = replaceKeywordValue(jsonString, "$CONSENTDETACHEDSIGNATUREFORNULLACCEPTEDUSER1CLAIM$",
					generateDetachedSignature(jsonString, BINDINGFORNULLACCEPTEDUSER1CLAIMJWK, BINDINGCERTFORNULLACCEPTEDUSER1CLAIMFILE));
		}
		
		if (jsonString.contains("$PROOFJWT$")) {

			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK1);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey1 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey1 =" + oidcJWKKey1);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}

			JSONObject request = new JSONObject(jsonString);
			String clientId = "";
			String accessToken = "";
			if (request.has("client_id")) {
				clientId = request.getString("client_id");
				request.remove("client_id");
			}
			if (request.has("idpAccessToken")) {
				accessToken = request.getString("idpAccessToken");
			}
			jsonString = request.toString();
			jsonString = replaceKeywordValue(jsonString, "$PROOFJWT$",
					signJWK(clientId, accessToken, oidcJWKKey1, testCaseName));
		}
		
		if (jsonString.contains("$PROOF_JWT_2$")) {

			String oidcJWKKeyString = JWKKeyUtil.getJWKKey(OIDCJWK4);
			logger.info("oidcJWKKeyString =" + oidcJWKKeyString);
			try {
				oidcJWKKey4 = RSAKey.parse(oidcJWKKeyString);
				logger.info("oidcJWKKey4 =" + oidcJWKKey4);
			} catch (java.text.ParseException e) {
				logger.error(e.getMessage());
			}

			JSONObject request = new JSONObject(jsonString);
			String clientId = "";
			String accessToken = "";
			String tempUrl = "";
			if (request.has("client_id")) {
				clientId = request.getString("client_id");
				request.remove("client_id");
			}
			if (request.has("idpAccessToken")) {
				accessToken = request.getString("idpAccessToken");
			}
			jsonString = request.toString();
			tempUrl = getValueFromEsignetWellKnownEndPoint("issuer", EsignetConfigManager.getEsignetBaseUrl());
			jsonString = replaceKeywordValue(jsonString, "$PROOF_JWT_2$",
					signJWKForMock(clientId, accessToken, oidcJWKKey4, testCaseName, tempUrl));
		}
		
		return jsonString;
		
	}
	
	public static String replaceKeywordValue(String jsonString, String keyword, String value) {
		if (value != null && !value.isEmpty())
			return jsonString.replace(keyword, value);
		else {
			if (keyword.contains("$ID:"))
				throw new SkipException("Marking testcase as skipped as required field is empty " + keyword
						+ " please check the results of testcase: " + getTestCaseIDFromKeyword(keyword));
			else
				throw new SkipException("Marking testcase as skipped as required field is empty " + keyword);

		}
	}
	
	public static String getAuthTransactionId(String oidcTransactionId) {
		final String transactionId = oidcTransactionId.replaceAll("_|-", "");
		String lengthOfTransactionId = getValueFromEsignetActuator(
				EsignetConfigManager.getEsignetActuatorPropertySection(), "mosip.esignet.auth-txn-id-length");
		int authTransactionIdLength = lengthOfTransactionId != null ? Integer.parseInt(lengthOfTransactionId): 0;
	    final byte[] oidcTransactionIdBytes = transactionId.getBytes();
	    final byte[] authTransactionIdBytes = new byte[authTransactionIdLength];
	    int i = oidcTransactionIdBytes.length - 1;
	    int j = 0;
	    while(j < authTransactionIdLength) {
	        authTransactionIdBytes[j++] = oidcTransactionIdBytes[i--];
	        if(i < 0) { i = oidcTransactionIdBytes.length - 1; }
	    }
	    return new String(authTransactionIdBytes);
	}
	
	public static String getWlaToken(String individualId, RSAKey jwkKey, String certData)
			throws JoseException, JOSEException {
		String tempUrl = EsignetConfigManager.getproperty("validateBindingEndpoint");
		int idTokenExpirySecs = Integer
				.parseInt(getValueFromEsignetActuator(EsignetConfigManager.getEsignetActuatorPropertySection(),
						GlobalConstants.MOSIP_ESIGNET_ID_TOKEN_EXPIRE_SECONDS));
		Instant instant = Instant.now();
		long epochValue = instant.getEpochSecond();

		JSONObject payload = new JSONObject();
		payload.put("iss", "postman-inji");
		payload.put("aud", tempUrl);
		payload.put("sub", individualId);
		payload.put("iat", epochValue);
		payload.put("exp", epochValue + idTokenExpirySecs);

		X509Certificate certificate = (X509Certificate) convertToCertificate(certData);
		JsonWebSignature jwSign = new JsonWebSignature();
		if (certificate != null) {
			jwSign.setKeyIdHeaderValue(certificate.getSerialNumber().toString(10));
			jwSign.setX509CertSha256ThumbprintHeaderValue(certificate);
			jwSign.setPayload(payload.toString());
			jwSign.setAlgorithmHeaderValue(SIGN_ALGO);
			jwSign.setKey(jwkKey.toPrivateKey());
			jwSign.setDoKeyValidation(false);
			return jwSign.getCompactSerialization();
		}
		return "";
	}
	
	public static String signJWKKeyForMock(String clientId, RSAKey jwkKey) {
		String tempUrl = getValueFromEsignetWellKnownEndPoint("token_endpoint", EsignetConfigManager.getEsignetBaseUrl());
		int idTokenExpirySecs = Integer
				.parseInt(getValueFromEsignetActuator(EsignetConfigManager.getEsignetActuatorPropertySection(),
						GlobalConstants.MOSIP_ESIGNET_ID_TOKEN_EXPIRE_SECONDS));
		JWSSigner signer;

		try {
			signer = new RSASSASigner(jwkKey);

			Date currentTime = new Date();

			// Create a Calendar instance to manipulate time
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentTime);

			// Add one hour to the current time
			calendar.add(Calendar.HOUR_OF_DAY, (idTokenExpirySecs / 3600)); // Adding one hour

			// Get the updated expiration time
			Date expirationTime = calendar.getTime();

			JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject(clientId).audience(tempUrl).issuer(clientId)
					.issueTime(currentTime).expirationTime(expirationTime).jwtID(clientId).build();

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
	
	public static String getDetachedSignature(String[] acceptedClaims, String[] permittedScope, RSAKey jwkKey,
			String certData) throws JoseException, JOSEException {
		JSONObject payload = new JSONObject();
		String signedJWT = null;

		if (acceptedClaims != null && acceptedClaims instanceof String[]) {
			Arrays.sort(acceptedClaims);
			payload.put("accepted_claims", acceptedClaims);
		}

		if (permittedScope != null && permittedScope instanceof String[]) {
			Arrays.sort(permittedScope);
			payload.put("permitted_authorized_scopes", permittedScope);
		}

		X509Certificate certificate = (X509Certificate) convertToCertificate(certData);
		JsonWebSignature jwSign = new JsonWebSignature();
		if (certificate != null) {
			jwSign.setX509CertSha256ThumbprintHeaderValue(certificate);
			jwSign.setPayload(payload.toString());
			jwSign.setAlgorithmHeaderValue(SIGN_ALGO);
			jwSign.setKey(jwkKey.toPrivateKey());
			jwSign.setDoKeyValidation(false);
			signedJWT = jwSign.getCompactSerialization();
			String[] parts = signedJWT.split("\\.");

			return parts[0] + "." + parts[2];
		}
		return "";
	}
	
	public static String generateDetachedSignature(String jsonString, String jwkKeyName, String certKeyName) {
		RSAKey jwkKey = null;
		String jwkKeyString = JWKKeyUtil.getJWKKey(jwkKeyName);
		logger.info("jwkKeyString =" + jwkKeyString);

		String[] acceptedClaims = null;
		JSONArray claimJsonArray = null;
		String[] permittedScope = null;
		JSONArray permittedScopeArray = null;
		String detachedSignature = "";
		String certificate = CertsUtil.getCertificate(certKeyName);
		JSONObject request = new JSONObject(jsonString);
		claimJsonArray = getArrayFromJson(request, "acceptedClaims");
		permittedScopeArray = getArrayFromJson(request, "permittedAuthorizeScopes");

		acceptedClaims = new String[claimJsonArray.length()];
		permittedScope = new String[permittedScopeArray.length()];

		for (int i = 0; i < claimJsonArray.length(); i++) {
			acceptedClaims[i] = claimJsonArray.getString(i);
		}
		if (acceptedClaims != null && acceptedClaims instanceof String[]) {
			Arrays.sort(acceptedClaims);
		}

		for (int i = 0; i < permittedScopeArray.length(); i++) {
			permittedScope[i] = permittedScopeArray.getString(i);
		}

		try {
			jwkKey = RSAKey.parse(jwkKeyString);
			logger.info("jwkKey =" + jwkKey);
			detachedSignature = getDetachedSignature(acceptedClaims, permittedScope, jwkKey, certificate);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return detachedSignature;

	}
	
	public static String generateWLAToken(String jsonString, String jwkKeyName, String certKeyName) {
		RSAKey jwkKey = null;
		String jwkKeyString = JWKKeyUtil.getJWKKey(jwkKeyName);
		logger.info("jwkKeyString =" + jwkKeyString);

		String individualId = "";
		String wlaToken = "";
		String certificate = CertsUtil.getCertificate(certKeyName);
		JSONObject request = new JSONObject(jsonString);
		individualId = request.getJSONObject(GlobalConstants.REQUEST).get(GlobalConstants.INDIVIDUALID).toString();

		try {
			jwkKey = RSAKey.parse(jwkKeyString);
			logger.info("jwkKey =" + jwkKey);
			wlaToken = getWlaToken(individualId, jwkKey, certificate);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return wlaToken;
	}
	
	public static String signJWK(String clientId, String accessToken, RSAKey jwkKey, String testCaseName) {
		String tempUrl = getValueFromEsignetWellKnownEndPoint("issuer", EsignetConfigManager.getEsignetBaseUrl());
		int idTokenExpirySecs = Integer
				.parseInt(getValueFromEsignetActuator(EsignetConfigManager.getEsignetActuatorPropertySection(),
						GlobalConstants.MOSIP_ESIGNET_ID_TOKEN_EXPIRE_SECONDS));
		JWSSigner signer;
		String proofJWT = "";
		String typ = "openid4vci-proof+jwt";
		JWK jwkHeader = jwkKey.toPublicJWK();
		SignedJWT signedJWT = null;

		try {
			signer = new RSASSASigner(jwkKey);
			Date currentTime = new Date();

			// Create a Calendar instance to manipulate time
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentTime);

			// Add one hour to the current time
			calendar.add(Calendar.HOUR_OF_DAY, (idTokenExpirySecs / 3600)); // Adding one hour

			// Get the updated expiration time
			Date expirationTime = calendar.getTime();

			String[] jwtParts = accessToken.split("\\.");
			String jwtPayloadBase64 = jwtParts[1];
			byte[] jwtPayloadBytes = Base64.getDecoder().decode(jwtPayloadBase64);
			String jwtPayload = new String(jwtPayloadBytes, StandardCharsets.UTF_8);
			JWTClaimsSet claimsSet = null;
			String nonce = new ObjectMapper().readTree(jwtPayload).get("c_nonce").asText();

			if (testCaseName.contains("_Invalid_C_nonce_"))
				nonce = "jwt_payload.c_nonce123";
			else if (testCaseName.contains("_Empty_C_nonce_"))
				nonce = "";
			else if (testCaseName.contains("_SpaceVal_C_nonce_"))
				nonce = "  ";
			else if (testCaseName.contains("_Empty_Typ_"))
				typ = "";
			else if (testCaseName.contains("_SpaceVal_Typ_"))
				typ = "  ";
			else if (testCaseName.contains("_Invalid_Typ_"))
				typ = "openid4vci-123@proof+jwt";
			else if (testCaseName.contains("_Invalid_JwkHeader_"))
				jwkHeader = RSAKey.parse(JWKKeyUtil.getJWKKey(OIDCJWK2)).toPublicJWK();
			else if (testCaseName.contains("_Invalid_Aud_"))
				tempUrl = "sdfaf";
			else if (testCaseName.contains("_Empty_Aud_"))
				tempUrl = "";
			else if (testCaseName.contains("_SpaceVal_Aud_"))
				tempUrl = "  ";
			else if (testCaseName.contains("_Invalid_Iss_"))
				clientId = "sdfdsg";
			else if (testCaseName.contains("_Invalid_Exp_"))
				idTokenExpirySecs = 0;

			claimsSet = new JWTClaimsSet.Builder().audience(tempUrl).claim("nonce", nonce).issuer(clientId)
					.issueTime(currentTime).expirationTime(expirationTime).jwtID(clientId).build();

			if (testCaseName.contains("_Missing_Typ_")) {
				signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).jwk(jwkHeader).build(), claimsSet);
			} else if (testCaseName.contains("_Missing_JwkHeader_")) {
				signedJWT = new SignedJWT(
						new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(typ)).build(), claimsSet);
			} else {
				signedJWT = new SignedJWT(
						new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(typ)).jwk(jwkHeader).build(),
						claimsSet);
			}

			signedJWT.sign(signer);
			proofJWT = signedJWT.serialize();
		} catch (Exception e) {
			logger.error("Exception while signing proof_jwt to get credential: " + e.getMessage());
		}
		return proofJWT;
	}
	
	public static String signJWKForMock(String clientId, String accessToken, RSAKey jwkKey, String testCaseName,
			String tempUrl) {
		int idTokenExpirySecs = Integer
				.parseInt(getValueFromEsignetActuator(EsignetConfigManager.getEsignetActuatorPropertySection(),
						GlobalConstants.MOSIP_ESIGNET_ID_TOKEN_EXPIRE_SECONDS));
		JWSSigner signer;
		String proofJWT = "";
		String typ = "openid4vci-proof+jwt";
		JWK jwkHeader = jwkKey.toPublicJWK();
		SignedJWT signedJWT = null;

		try {
			signer = new RSASSASigner(jwkKey);
			Date currentTime = new Date();

			// Create a Calendar instance to manipulate time
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentTime);

			// Add one hour to the current time
			calendar.add(Calendar.HOUR_OF_DAY, (idTokenExpirySecs / 3600)); // Adding one hour

			// Get the updated expiration time
			Date expirationTime = calendar.getTime();

			String[] jwtParts = accessToken.split("\\.");
			String jwtPayloadBase64 = jwtParts[1];
			byte[] jwtPayloadBytes = Base64.getDecoder().decode(jwtPayloadBase64);
			String jwtPayload = new String(jwtPayloadBytes, StandardCharsets.UTF_8);
			JWTClaimsSet claimsSet = null;
			String nonce = new ObjectMapper().readTree(jwtPayload).get("c_nonce").asText();

			claimsSet = new JWTClaimsSet.Builder().audience(tempUrl).claim("nonce", nonce).issuer(clientId)
					.issueTime(currentTime).expirationTime(expirationTime).jwtID(clientId).build();
			signedJWT = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(typ)).jwk(jwkHeader).build(),
					claimsSet);

			signedJWT.sign(signer);
			proofJWT = signedJWT.serialize();
		} catch (Exception e) {
			logger.error("Exception while signing proof_jwt to get credential: " + e.getMessage());
		}
		return proofJWT;
	}
	
	public static String signJWKKey(String clientId, RSAKey jwkKey, String tempUrl) {
		int idTokenExpirySecs = Integer
				.parseInt(getValueFromEsignetActuator(EsignetConfigManager.getEsignetActuatorPropertySection(),
						GlobalConstants.MOSIP_ESIGNET_ID_TOKEN_EXPIRE_SECONDS));
		JWSSigner signer;

		try {
			signer = new RSASSASigner(jwkKey);

			Date currentTime = new Date();

			// Create a Calendar instance to manipulate time
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentTime);

			// Add one hour to the current time
			calendar.add(Calendar.HOUR_OF_DAY, (idTokenExpirySecs / 3600)); // Adding one hour

			// Get the updated expiration time
			Date expirationTime = calendar.getTime();

			JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject(clientId).audience(tempUrl).issuer(clientId)
					.issueTime(currentTime).expirationTime(expirationTime).jwtID(clientId).build();

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
	
	public static String getValueFromEsignetWellKnownEndPoint(String key, String baseURL) {
		String url = baseURL + EsignetConfigManager.getproperty("esignetWellKnownEndPoint");
		Response response = null;
		JSONObject responseJson = null;
		if (responseJson == null) {
			try {
				response = RestClient.getRequest(url, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
				responseJson = new org.json.JSONObject(response.getBody().asString());
				return responseJson.getString(key);
			} catch (Exception e) {
				logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			}
		}
		return responseJson.getString(key);
	}
	
	private static final String SIGN_ALGO = "RS256";

	public static String clientAssertionToken;
	
	protected static final String BINDINGJWK1 = "bindingJWK1";
	protected static final String BINDINGJWKVID = "bindingJWKVid";
	protected static final String BINDINGCONSENTJWK = "bindingConsentJWK";
	protected static final String BINDINGCONSENTJWKVID = "bindingConsentJWKVid";
	protected static final String BINDINGCONSENTSAMECLAIMJWK = "bindingConsentSameClaimJWK";
	protected static final String BINDINGCONSENTVIDSAMECLAIMJWK = "bindingConsentVidSameClaimJWK";
	protected static final String BINDINGCONSENTEMPTYCLAIMJWK = "bindingConsentEmptyClaimJWK";
	protected static final String BINDINGCONSENTUSER2JWK = "bindingConsentUser2JWK";
	protected static final String BINDINGCONSENTVIDUSER2JWK = "bindingConsentVidUser2JWK";
	protected static final String BINDINGCONSENTACCEPTEDCLAIMJWK = "bindingConsentAcceptedClaimJWK";
	protected static final String BINDINGCONSENTFORACCEPTEDCLAIMJWK = "bindingConsentForAcceptedClaimJWK";
	protected static final String BINDINGCONSENTFOROPTIONALCLAIMJWK = "bindingConsentForOptionalClaimJWK";
	protected static final String BINDINGCONSENTFORPARTIALESSENTIALOPTIONALCLAIMJWK = "bindingConsentForPartialEssentialOptionalClaimJWK";
	protected static final String BINDINGCONSENTFORONLYESSENTIALCLAIMJWK = "bindingConsentForOnlyEssentialClaimJWK";
	protected static final String BINDINGCONSENTFORNULLESSENTIALCLAIMJWK = "bindingConsentForNullEssentialClaimJWK";
	protected static final String BINDINGCONSENTFOROPTIONALACCEPTEDCLAIMJWK = "bindingConsentForOptionalAcceptedClaimJWK";
	protected static final String BINDINGCONSENTFORNULLACCEPTEDCLAIMJWK = "bindingConsentForNullAcceptedClaimJWK";
	protected static final String BINDINGFOROPTIONALUSER1CLAIMJWK = "bindingForOptionalUser1ClaimJWK";
	protected static final String BINDINGFORPARTIALESSENTIALOPTIONALUSER1CLAIMJWK = "bindingForPartialEssentialOptionalUser1ClaimJWK";
	protected static final String BINDINGFORONLYESSENTIALUSER1CLAIMJWK = "bindingForOnlyEssentialUser1ClaimJWK";
	protected static final String BINDINGFORNULLESSENTIALUSER1CLAIMJWK = "bindingForNullEssentialUser1ClaimJWK";
	protected static final String BINDINGFOROPTIONALACCEPTEDUSER1CLAIMJWK = "bindingForOptionalAcceptedUser1ClaimJWK";
	protected static final String BINDINGFORNULLACCEPTEDUSER1CLAIMJWK = "bindingForNullAcceptedUser1ClaimJWK";
	
	
	public static final String BINDINGCERTFILE = "BINDINGCERTFile";
	public static final String BINDINGCERTFILEVID = "BINDINGCERTFileVid";
	public static final String BINDINGCERTCONSENTFILE = "BINDINGCERTCONSENTFile";
	public static final String BINDINGCERTCONSENTVIDFILE = "BINDINGCERTCONSENTVidFile";
	public static final String BINDINGCERTCONSENTSAMECLAIMFILE = "BINDINGCERTCONSENTSAMECLAIMFile";
	public static final String BINDINGCERTCONSENTVIDSAMECLAIMFILE = "BINDINGCERTCONSENTVIDSAMECLAIMFile";
	public static final String BINDINGCERTCONSENTEMPTYCLAIMFILE = "BINDINGCERTCONSENTEMPTYCLAIMFile";
	public static final String BINDINGCERTCONSENTUSER2FILE = "BINDINGCERTCONSENTUSER2File";
	public static final String BINDINGCERTVIDCONSENTUSER2FILE = "BINDINGCERTCONSENTVIDUSER2File";
	public static final String BINDINGCERTCONSENTACCEPTEDCLAIMFILE = "BINDINGCERTCONSENTACCEPTEDCLAIMFile";
	public static final String BINDINGCERTCONSENTFORACCEPTEDCLAIMFILE = "BINDINGCERTCONSENTFORACCEPTEDCLAIMFile";
	public static final String BINDINGCERTCONSENTFOROPTIONALCLAIMFILE = "BINDINGCERTCONSENTFOROPTIONALCLAIMFile";
	public static final String BINDINGCERTCONSENTFORPARTIALESSENTIALOPTIONALCLAIMFILE = "BINDINGCERTCONSENTFORPARTIALESSENTIALOPTIONALCLAIMFile";
	public static final String BINDINGCERTCONSENTFORONLYESSENTIALCLAIMFILE = "BINDINGCERTCONSENTFORONLYESSENTIALCLAIMFile";
	public static final String BINDINGCERTCONSENTFORNULLESSENTIALCLAIMFILE = "BINDINGCERTCONSENTFORNULLESSENTIALCLAIMFile";
	public static final String BINDINGCERTCONSENTFOROPTIONALACCEPTEDCLAIMFILE = "BINDINGCERTCONSENTFOROPTIONALACCEPTEDCLAIMFile";
	public static final String BINDINGCERTCONSENTFORNULLACCEPTEDCLAIMFILE = "BINDINGCERTCONSENTFORNULLACCEPTEDCLAIMFile";
	public static final String BINDINGCERTFOROPTIONALUSER1CLAIMFILE = "BINDINGCERTFOROPTIONALUSER1CLAIMFILE";
	public static final String BINDINGCERTFORPARTIALESSENTIALOPTIONALUSER1CLAIMFILE = "BINDINGCERTFORPARTIALESSENTIALOPTIONALUSER1CLAIMFILE";
	public static final String BINDINGCERTFORONLYESSENTIALUSER1CLAIMFILE = "BINDINGCERTFORONLYESSENTIALUSER1CLAIMFILE";
	public static final String BINDINGCERTFORNULLESSENTIALUSER1CLAIMFILE = "BINDINGCERTFORNULLESSENTIALUSER1CLAIMFILE";
	public static final String BINDINGCERTFOROPTIONALACCEPTEDUSER1CLAIMFILE = "BINDINGCERTFOROPTIONALACCEPTEDUSER1CLAIMFILE";
	public static final String BINDINGCERTFORNULLACCEPTEDUSER1CLAIMFILE = "BINDINGCERTFORNULLACCEPTEDUSER1CLAIMFILE";
	
	protected static final String OIDCJWK1 = "oidcJWK1";
	protected static final String OIDCJWK2 = "oidcJWK2";
	protected static final String OIDCJWK3 = "oidcJWK3";
	protected static final String OIDCJWK4 = "oidcJWK4";
	protected static final String OIDCJWK5 = "oidcJWK5";
	protected static final String OIDCJWK6 = "oidcJWK6";
	protected static final String OIDCJWK7= "oidcJWK7";
	protected static final String OIDCJWK8= "oidcJWK8";
	protected static final String OIDCJWK9= "oidcJWK9";
	
	protected static RSAKey oidcJWKKey1 = null;
	protected static RSAKey oidcJWKKey3 = null;
	protected static RSAKey oidcJWKKey4 = null;
	protected static RSAKey oidcJWKKey5 = null;
	protected static RSAKey oidcJWKKey6 = null;
	protected static RSAKey oidcJWKKey7 = null;
	protected static RSAKey oidcJWKKey8 = null;
	protected static RSAKey oidcJWKKey9 = null;
	
	protected static boolean triggerESignetKeyGen1 = true;
	protected static boolean triggerESignetKeyGen2 = true;
	protected static boolean triggerESignetKeyGen3 = true;
	protected static boolean triggerESignetKeyGen4 = true;
	protected static boolean triggerESignetKeyGen5 = true;
	protected static boolean triggerESignetKeyGen6 = true;
	protected static boolean triggerESignetKeyGen7 = true;
	protected static boolean triggerESignetKeyGen8 = true;
	protected static boolean triggerESignetKeyGen9 = true;
	protected static boolean triggerESignetKeyGen10 = true;
	protected static boolean triggerESignetKeyGen11 = true;
	protected static boolean triggerESignetKeyGen12 = true;
	protected static boolean triggerESignetKeyGen13 = true;
	protected static boolean triggerESignetKeyGen15 = true;
	protected static boolean triggerESignetKeyGen16 = true;
	protected static boolean triggerESignetKeyGen17 = true;
	protected static boolean triggerESignetKeyGen18 = true;
	protected static boolean triggerESignetKeyGen19 = true;
	protected static boolean triggerESignetKeyGen20 = true;
	protected static boolean triggerESignetKeyGen21 = true;
	protected static boolean triggerESignetKeyGen22 = true;
	protected static boolean triggerESignetKeyGen23 = true;
	protected static boolean triggerESignetKeyGen24 = true;
	protected static boolean triggerESignetKeyGen25 = true;
	protected static boolean triggerESignetKeyGen26 = true;
	protected static boolean triggerESignetKeyGen27 = true;
	protected static boolean triggerESignetKeyGen28 = true;
	protected static boolean triggerESignetKeyGen29 = true;
	protected static boolean triggerESignetKeyGen30 = true;
	protected static boolean triggerESignetKeyGen31 = true;
	protected static boolean triggerESignetKeyGen32 = true;
	protected static boolean triggerESignetKeyGen33 = true;
	
	
	private static boolean gettriggerESignetKeyGen3() {
		return triggerESignetKeyGen3;
	}
	
	private static void settriggerESignetKeyGen3(boolean value) {
		triggerESignetKeyGen3 = value;
	}
	
	private static boolean gettriggerESignetKeyGen4() {
		return triggerESignetKeyGen4;
	}
	
	private static void settriggerESignetKeyGen4(boolean value) {
		triggerESignetKeyGen4 = value;
	}
	
	private static boolean gettriggerESignetKeyGen5() {
		return triggerESignetKeyGen5;
	}
	
	private static void settriggerESignetKeyGen5(boolean value) {
		triggerESignetKeyGen5 = value;
	}
	
	private static boolean gettriggerESignetKeyGen6() {
		return triggerESignetKeyGen6;
	}
	
	private static void settriggerESignetKeyGen6(boolean value) {
		triggerESignetKeyGen6 = value;
	}
	
	private static boolean gettriggerESignetKeyGen7() {
		return triggerESignetKeyGen7;
	}
	
	private static void settriggerESignetKeyGen7(boolean value) {
		triggerESignetKeyGen7 = value;
	}
	
	private static boolean gettriggerESignetKeyGen8() {
		return triggerESignetKeyGen8;
	}
	
	private static void settriggerESignetKeyGen8(boolean value) {
		triggerESignetKeyGen8 = value;
	}
	
	private static boolean gettriggerESignetKeyGen9() {
		return triggerESignetKeyGen9;
	}
	
	private static void settriggerESignetKeyGen9(boolean value) {
		triggerESignetKeyGen9 = value;
	}
	
	private static boolean gettriggerESignetKeyGen10() {
		return triggerESignetKeyGen10;
	}
	
	private static void settriggerESignetKeyGen10(boolean value) {
		triggerESignetKeyGen10 = value;
	}
	
	private static boolean gettriggerESignetKeyGen11() {
		return triggerESignetKeyGen11;
	}
	
	private static void settriggerESignetKeyGen11(boolean value) {
		triggerESignetKeyGen11 = value;
	}
	
	private static boolean gettriggerESignetKeyGen1() {
		return triggerESignetKeyGen1;
	}
	
	private static void settriggerESignetKeyGen1(boolean value) {
		triggerESignetKeyGen1 = value;
	}
	
	private static void settriggerESignetKeyGen2(boolean value) {
		triggerESignetKeyGen2 = value;
	}

	private static boolean gettriggerESignetKeyGen2() {
		return triggerESignetKeyGen2;
	}
	
	private static void settriggerESignetKeyGen12(boolean value) {
		triggerESignetKeyGen12 = value;
	}

	private static boolean gettriggerESignetKeyGen12() {
		return triggerESignetKeyGen12;
	}
	
	private static void settriggerESignetKeyGen13(boolean value) {
		triggerESignetKeyGen13 = value;
	}

	private static boolean gettriggerESignetKeyGen13() {
		return triggerESignetKeyGen13;
	}
	
	private static void settriggerESignetKeyGen15(boolean value) {
		triggerESignetKeyGen15 = value;
	}
	
	private static boolean gettriggerESignetKeyGen15() {
		return triggerESignetKeyGen15;
	}
	private static void settriggerESignetKeyGen16(boolean value) {
		triggerESignetKeyGen16 = value;
	}
	
	private static boolean gettriggerESignetKeyGen16() {
		return triggerESignetKeyGen16;
	}
	private static void settriggerESignetKeyGen17(boolean value) {
		triggerESignetKeyGen17 = value;
	}
	
	private static boolean gettriggerESignetKeyGen17() {
		return triggerESignetKeyGen17;
	}
	
	private static void settriggerESignetKeyGen18(boolean value) {
		triggerESignetKeyGen18 = value;
	}
	
	private static boolean gettriggerESignetKeyGen18() {
		return triggerESignetKeyGen18;
	}
	
	private static void settriggerESignetKeyGen19(boolean value) {
		triggerESignetKeyGen19 = value;
	}
	
	private static boolean gettriggerESignetKeyGen19() {
		return triggerESignetKeyGen19;
	}
	
	private static void settriggerESignetKeyGen20(boolean value) {
		triggerESignetKeyGen20 = value;
	}
	
	private static boolean gettriggerESignetKeyGen20() {
		return triggerESignetKeyGen20;
	}
	
	private static void settriggerESignetKeyGen21(boolean value) {
		triggerESignetKeyGen21 = value;
	}
	
	private static boolean gettriggerESignetKeyGen21() {
		return triggerESignetKeyGen21;
	}
	
	private static void settriggerESignetKeyGen22(boolean value) {
		triggerESignetKeyGen22 = value;
	}
	
	private static boolean gettriggerESignetKeyGen22() {
		return triggerESignetKeyGen22;
	}
	
	private static void settriggerESignetKeyGen23(boolean value) {
		triggerESignetKeyGen23 = value;
	}
	
	private static boolean gettriggerESignetKeyGen23() {
		return triggerESignetKeyGen23;
	}
	
	private static void settriggerESignetKeyGen24(boolean value) {
		triggerESignetKeyGen24 = value;
	}
	
	private static boolean gettriggerESignetKeyGen24() {
		return triggerESignetKeyGen24;
	}
	
	private static void settriggerESignetKeyGen25(boolean value) {
		triggerESignetKeyGen25 = value;
	}
	
	private static boolean gettriggerESignetKeyGen25() {
		return triggerESignetKeyGen25;
	}
	
	private static void settriggerESignetKeyGen26(boolean value) {
		triggerESignetKeyGen26 = value;
	}
	
	private static boolean gettriggerESignetKeyGen26() {
		return triggerESignetKeyGen26;
	}
	
	private static void settriggerESignetKeyGen27(boolean value) {
		triggerESignetKeyGen27 = value;
	}
	
	private static boolean gettriggerESignetKeyGen27() {
		return triggerESignetKeyGen27;
	}
	
	private static void settriggerESignetKeyGen28(boolean value) {
		triggerESignetKeyGen28 = value;
	}
	
	private static boolean gettriggerESignetKeyGen28() {
		return triggerESignetKeyGen28;
	}
	
	private static void settriggerESignetKeyGen29(boolean value) {
		triggerESignetKeyGen29 = value;
	}
	
	private static boolean gettriggerESignetKeyGen29() {
		return triggerESignetKeyGen29;
	}
	
	private static void settriggerESignetKeyGen30(boolean value) {
		triggerESignetKeyGen30 = value;
	}
	
	private static boolean gettriggerESignetKeyGen30() {
		return triggerESignetKeyGen30;
	}
	
	private static void settriggerESignetKeyGen31(boolean value) {
		triggerESignetKeyGen31 = value;
	}
	
	private static boolean gettriggerESignetKeyGen31() {
		return triggerESignetKeyGen31;
	}
	
	private static void settriggerESignetKeyGen32(boolean value) {
		triggerESignetKeyGen32 = value;
	}
	
	private static boolean gettriggerESignetKeyGen32() {
		return triggerESignetKeyGen32;
	}
	
	private static void settriggerESignetKeyGen33(boolean value) {
		triggerESignetKeyGen33 = value;
	}
	
	private static boolean gettriggerESignetKeyGen33() {
		return triggerESignetKeyGen33;
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
	
	private static Response sendPostRequest(String url, Map<String, String> params) {
		try {
			return RestClient.postRequestWithFormDataBody(url, params);
		} catch (Exception e) {
			logger.error("Error sending POST request to URL: " + url, e);
			return null;
		}
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
        if (role == null) return "";

        String roleLowerCase = role.toLowerCase();
        switch (roleLowerCase) {
            case "partner":
                if (!AdminTestUtil.isValidToken(partnerCookie)) {
                    partnerCookie = getAuthTokenFromKeyCloak(EsignetConfigManager.getPmsClientId(), EsignetConfigManager.getPmsClientSecret());
                }
                return partnerCookie;
            case "mobileauth":
                if (!AdminTestUtil.isValidToken(mobileAuthCookie)) {
                    mobileAuthCookie = getAuthTokenFromKeyCloak(EsignetConfigManager.getMPartnerMobileClientId(), EsignetConfigManager.getMPartnerMobileClientSecret());
                }
                return mobileAuthCookie;
            default:
                return "";
        }
    }

	public static Response postRequestWithCookieAndAuthHeader(String url, String jsonInput, String cookieName, String role,
			String testCaseName) {
		Response response = null;
		token = getAuthTokenByRole(role);
		String apiKey = null;
		String partnerId = null;
		JSONObject req = new JSONObject(jsonInput);
		apiKey = req.getString(GlobalConstants.APIKEY);
		req.remove(GlobalConstants.APIKEY);
		partnerId = req.getString(GlobalConstants.PARTNERID);
		req.remove(GlobalConstants.PARTNERID);

		HashMap<String, String> headers = new HashMap<>();
		headers.put("PARTNER-API-KEY", apiKey);
		headers.put("PARTNER-ID", partnerId);
		headers.put(cookieName, "Bearer " + token);
		jsonInput = req.toString();
		if (BaseTestCase.currentModule.equals(GlobalConstants.ESIGNET)) {
			jsonInput = smtpOtpHandler(jsonInput, testCaseName);
		}

		logger.info(GlobalConstants.POST_REQ_URL + url);
		GlobalMethods.reportRequest(headers.toString(), jsonInput, url);
		try {
			response = RestClient.postRequestWithMultipleHeadersWithoutCookie(url, jsonInput,
					MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, headers);
			GlobalMethods.reportResponse(response.getHeaders().asList().toString(), url, response);
			return response;
		} catch (Exception e) {
			logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			return response;
		}
	}
	
	public static Response postWithBodyAndBearerToken(String url, String jsonInput, String cookieName,
			String role, String testCaseName, String idKeyName) {
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
	
	public static Response putWithPathParamsAndBodyAndBearerToken(String url, String jsonInput, String cookieName, String role,
			String testCaseName, String pathParams) {
		Response response = null;
		logger.info("inputJson is::" + jsonInput);
		JSONObject req = new JSONObject(jsonInput);
		logger.info(GlobalConstants.REQ_STR + req);
		HashMap<String, String> pathParamsMap = new HashMap<>();
		String[] params = pathParams.split(",");
		for (String param : params) {
			logger.info("param is::" + param);
			if (req.has(param)) {
				logger.info(GlobalConstants.REQ_STR + req);
				pathParamsMap.put(param, req.get(param).toString());
				req.remove(param);
			} else
				logger.error(GlobalConstants.ERROR_STRING_2 + param + GlobalConstants.IN_STRING + jsonInput);
		}
		if (testCaseName.contains("Invalid_Token")) {
			token = "xyz";
		} else {
			token = getAuthTokenByRole(role);
		}
		logger.info(GlobalConstants.PUT_REQ_STRING + url);
		GlobalMethods.reportRequest(null, req.toString(), url);
		try {
			response = RestClient.putWithPathParamsBodyAndBearerToken(url, pathParamsMap, req.toString(),
					MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, cookieName, token);
			GlobalMethods.reportResponse(response.getHeaders().asList().toString(), url, response);
			return response;
		} catch (Exception e) {
			logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			return response;
		}
	}
	
	protected Response postRequestWithCookieAuthHeaderForAutoGenId(String url, String jsonInput, String cookieName,
			String role, String testCaseName, String idKeyName) {
		Response response = null;
		String inputJson = inputJsonKeyWordHandeler(jsonInput, testCaseName);
		token = "";
		if (EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase()
				.contains("mockauthenticationservice") == true) {
			token = getAuthTokenByRole(role);
		}else {
			token = kernelAuthLib.getTokenByRole(role);
		}
		String apiKey = null;
		String partnerId = null;
		JSONObject req = new JSONObject(inputJson);
		apiKey = req.getString(GlobalConstants.APIKEY);
		req.remove(GlobalConstants.APIKEY);
		partnerId = req.getString(GlobalConstants.PARTNERID);
		req.remove(GlobalConstants.PARTNERID);

		HashMap<String, String> headers = new HashMap<>();
		headers.put("PARTNER-API-KEY", apiKey);
		headers.put("PARTNER-ID", partnerId);
		headers.put(cookieName, "Bearer " + token);
		inputJson = req.toString();
		if (BaseTestCase.currentModule.equals(GlobalConstants.ESIGNET)) {
			inputJson = smtpOtpHandler(inputJson, testCaseName);
		}
		logger.info(GlobalConstants.POST_REQ_URL + url);
		GlobalMethods.reportRequest(headers.toString(), inputJson, url);
		try {
			response = RestClient.postRequestWithMultipleHeadersWithoutCookie(url, inputJson,
					MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, headers);
			GlobalMethods.reportResponse(response.getHeaders().asList().toString(), url, response);
			if (testCaseName.toLowerCase().contains("_sid")) {
				writeAutoGeneratedId(response, idKeyName, testCaseName);
			}

			if (testCaseName.toLowerCase().contains("_scert")) {
				cacheCertificate(response, testCaseName);
			}
			return response;
		} catch (Exception e) {
			logger.error(GlobalConstants.EXCEPTION_STRING_2 + e);
			return response;
		}
	}

	public static void cacheCertificate(Response response, String testCaseName) {
		String certsKey = null;
		if (testCaseName.contains("Wla_uin_")) {
			certsKey = BINDINGCERTFILE;
		} else if (testCaseName.contains("Wla_Vid_")) {
			certsKey = BINDINGCERTFILEVID;
		} else if (testCaseName.contains("_Consentuin_")) {
			certsKey = BINDINGCERTCONSENTFILE;
		} else if (testCaseName.contains("_ConsentVid_")) {
			certsKey = BINDINGCERTCONSENTVIDFILE;
		} else if (testCaseName.contains("_Consent_SameClaim_uin_")) {
			certsKey = BINDINGCERTCONSENTSAMECLAIMFILE;
		} else if (testCaseName.contains("_Consent_SameClaim_Vid_")) {
			certsKey = BINDINGCERTCONSENTVIDSAMECLAIMFILE;
		} else if (testCaseName.contains("_Consent_EmptyClaim_uin_")) {
			certsKey = BINDINGCERTCONSENTEMPTYCLAIMFILE;
		} else if (testCaseName.contains("_Consent_User2_uin_SCert_")) {
			certsKey = BINDINGCERTCONSENTUSER2FILE;
		} else if (testCaseName.contains("_Consent_User2_Vid_SCert_")) {
			certsKey = BINDINGCERTVIDCONSENTUSER2FILE;
		} else if (testCaseName.contains("_SCert_InvalidAcceptedClaim_")) {
			certsKey = BINDINGCERTCONSENTACCEPTEDCLAIMFILE;
		} else if (testCaseName.contains("_Consent_forAcceptedClaims_SCert_")) {
			certsKey = BINDINGCERTCONSENTFORACCEPTEDCLAIMFILE;
		} else if (testCaseName.contains("_Consent_forOptionalClaims_SCert_")) {
			certsKey = BINDINGCERTCONSENTFOROPTIONALCLAIMFILE;
		} else if (testCaseName.contains("_Consent_forPartialEssentialOptionalClaims_SCert_")) {
			certsKey = BINDINGCERTCONSENTFORPARTIALESSENTIALOPTIONALCLAIMFILE;
        } else if (testCaseName.contains("_Consent_forOnlyEssentialClaims_SCert_")) {
			certsKey = BINDINGCERTCONSENTFORONLYESSENTIALCLAIMFILE;
        } else if (testCaseName.contains("_Consent_forNullAsEssentialClaims_SCert_")) {
        	certsKey = BINDINGCERTCONSENTFORNULLESSENTIALCLAIMFILE;
        } else if (testCaseName.contains("_Consent_forOnlyOptionalClaimAsRequested_SCert_")) {
        	certsKey = BINDINGCERTCONSENTFOROPTIONALACCEPTEDCLAIMFILE;
        } else if (testCaseName.contains("_Consent_without_AcceptedClaim_IfOnlyOptionalRequested_SCert_")) {
        	certsKey = BINDINGCERTCONSENTFORNULLACCEPTEDCLAIMFILE;
        } else if (testCaseName.contains("_withOnlyOptionalClaim_IfEssAndOptAsRequestedClaim_SCert_")) {
        	            certsKey = BINDINGCERTFOROPTIONALUSER1CLAIMFILE;
        } else if (testCaseName.contains("_forPartialEseentialAndOptionalClaim_SCert_")) {
        	certsKey = BINDINGCERTFORPARTIALESSENTIALOPTIONALUSER1CLAIMFILE;
        } else if (testCaseName.contains("_forOnlyEseentialClaim_SCert_")) {
        	certsKey = BINDINGCERTFORONLYESSENTIALUSER1CLAIMFILE;
        } else if (testCaseName.contains("_forEmptyAsAcceptedClaim_EseentialClaim_SCert_")) {
        	certsKey = BINDINGCERTFORNULLESSENTIALUSER1CLAIMFILE;
        } else if (testCaseName.contains("_forOnlyOptionalClaimIfRequestedOptional_SCert_")) {
        	certsKey = BINDINGCERTFOROPTIONALACCEPTEDUSER1CLAIMFILE;
        } else if (testCaseName.contains("_forEmptyAcceptedClaimIfRequestedIsOptional_SCert_")) {
        	certsKey = BINDINGCERTFORNULLACCEPTEDUSER1CLAIMFILE;
        }
		
		
		String certificateData = new JSONObject(response.getBody().asString()).getJSONObject(GlobalConstants.RESPONSE)
				.get("certificate").toString();

		CertsUtil.addCertificateToCache(certsKey, certificateData);
	}
	
	public static String generateFullNameForSunBirdR() {
		return faker.name().fullName();
	}

	public static String generateDobForSunBirdR() {
		Faker faker = new Faker();
		LocalDate dob = faker.date().birthday().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		return dob.format(formatter);
	}
}
