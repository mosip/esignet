package constants;

import utils.EsignetConfigManager;

public class UiConstants {
	public static final String SIGNUP_ACTUATOR_ENDPOINT_KEYWORD = "actuatorSignupEndpoint";

	public static final String ACTIVE_PROFILES = "activeProfiles";

	public static final String SYSTEM_ENV_SECTION = "systemEnvironment";

	public static final String CLASS_PATH_APPLICATION_PROPERTIES = "classpath:/application.properties";

	public static final String CLASS_PATH_APPLICATION_DEFAULT_PROPERTIES = "classpath:/application-default.properties";

	public static final String DEFAULT_STRING = "default";

	public static final String MOSIP_CONFIG_APPLICATION_HYPHEN_STRING = "mosip-config/application-";

	public static final String DOT_PROPERTIES_STRING = ".properties";

	public static final String SIGNUP_BASE_URL = EsignetConfigManager.getSignupUrl();

	public static final String SIGNUP_ACTUATOR_URL = SIGNUP_BASE_URL
			+ EsignetConfigManager.getproperty(SIGNUP_ACTUATOR_ENDPOINT_KEYWORD);
	
	public static final String SIGNUP_UI_SPEC_KEYWORD = "uiSpecEndpoint";
	
	public static final String SIGNUP_UI_SPEC_URL = SIGNUP_BASE_URL
			+ EsignetConfigManager.getproperty(SIGNUP_UI_SPEC_KEYWORD);

}