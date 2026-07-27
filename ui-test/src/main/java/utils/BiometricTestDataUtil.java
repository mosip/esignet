package utils;

/**
 * Optional biometric test identities for MOSIP-22718 negative flows.
 * When not configured, related steps log a skip and pass (data is env-specific).
 */
public final class BiometricTestDataUtil {

	private static final String DEFAULT_INVALID_ID = "8957093658024750";

	private BiometricTestDataUtil() {
	}

	public static String getInvalidUin() {
		return firstNonBlank("biometricInvalidUin", "invalidUin", DEFAULT_INVALID_ID);
	}

	public static String getInvalidVid() {
		return firstNonBlank("biometricInvalidVid", "invalidVid", DEFAULT_INVALID_ID);
	}

	public static String getExceptionUin() {
		return trimToNull(EsignetConfigManager.getproperty("biometricExceptionUin"));
	}

	public static String getExceptionVid() {
		return trimToNull(EsignetConfigManager.getproperty("biometricExceptionVid"));
	}

	public static String getWrongMatchUin() {
		return trimToNull(EsignetConfigManager.getproperty("biometricWrongMatchUin"));
	}

	public static String getWrongMatchVid() {
		return trimToNull(EsignetConfigManager.getproperty("biometricWrongMatchVid"));
	}

	public static boolean isTimeoutScenarioEnabled() {
		return Boolean.parseBoolean(EsignetConfigManager.getproperty("biometricTimeoutTestEnabled"));
	}

	private static String firstNonBlank(String primaryKey, String fallbackKey, String defaultValue) {
		String value = trimToNull(EsignetConfigManager.getproperty(primaryKey));
		if (value != null) {
			return value;
		}
		value = trimToNull(EsignetConfigManager.getproperty(fallbackKey));
		return value != null ? value : defaultValue;
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
