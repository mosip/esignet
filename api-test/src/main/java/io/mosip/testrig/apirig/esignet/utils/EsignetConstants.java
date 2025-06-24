package io.mosip.testrig.apirig.esignet.utils;

public class EsignetConstants {
	public static final String ACTIVE_PROFILES = "activeProfiles";

	public static final String ESIGNET_ACTUATOR_ENDPOINT_KEYWORD = "actuatorEsignetEndpoint";

	public static final String ESIGNET_BASE_URL = EsignetConfigManager.getEsignetBaseUrl();

	public static final String ESIGNET_ACTUATOR_URL = ESIGNET_BASE_URL
			+ EsignetConfigManager.getproperty(ESIGNET_ACTUATOR_ENDPOINT_KEYWORD);

	public static final String SYSTEM_ENV_SECTION = "systemEnvironment";
	
	public static final String CLASS_PATH_APPLICATION_PROPERTIES = "classpath:/application.properties";
	
	public static final String MOSIP_ESIGNET_CAPTCHA_REQUIRED = "mosip.esignet.captcha.required";
	
	public static final String CLASS_PATH_APPLICATION_DEFAULT_PROPERTIES = "classpath:/application-default.properties";

	public static final String DEFAULT_STRING = "default";
	
	public static final String MOSIP_CONFIG_APPLICATION_HYPHEN_STRING = "mosip-config/application-";
	
	public static final String DOT_PROPERTIES_STRING = ".properties";
	
	public static final String ESIGNET_SUPPORTED_LANGUAGE = "esignetSupportedLanguage";
	
	public static final String MOSIP_ESIGNET_SUPPORTED_CREDENTIAL_SCOPES_LANGUAGE = "mosip.esignet.supported.credential.scopes";
	
	public static final String USE_PRE_CONFIGURED_OTP_STRING = "usePreConfiguredOtp";
	
	public static final String PRE_CONFIGURED_OTP_STRING = "preconfiguredOtp";
	
	public static final String TRUE_STRING = "true";
	
	public static final String ALL_ONE_OTP_STRING = "111111";
	
	public static final String DSL = "dsl";

}
