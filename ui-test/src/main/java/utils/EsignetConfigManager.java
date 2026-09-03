package utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import io.mosip.testrig.apirig.utils.ConfigManager;

public class EsignetConfigManager extends io.mosip.testrig.apirig.utils.ConfigManager {

	private static final Logger LOGGER = Logger.getLogger(EsignetConfigManager.class);

	public static void init() {
		Logger configManagerLogger = Logger.getLogger(ConfigManager.class);
		configManagerLogger.setLevel(Level.WARN);

		Map<String, Object> moduleSpecificPropertiesMap = new HashMap<>();

		try {
			Properties configProps = new Properties();
			try (InputStream inputStream = EsignetConfigManager.class.getClassLoader()
					.getResourceAsStream("config.properties")) {
				if (inputStream == null) {
					LOGGER.error("config.properties resource not found in classpath");
					throw new FileNotFoundException("config.properties not found");
				}
				configProps.load(inputStream);
				LOGGER.info("Config properties loaded successfully.");
			} catch (IOException e) {
				LOGGER.error("Failed to load config.properties", e);
				throw new RuntimeException("Failed to load config.properties file", e);
			}

			for (String key : configProps.stringPropertyNames()) {
				moduleSpecificPropertiesMap.put(key, configProps.getProperty(key));
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}

		init(moduleSpecificPropertiesMap);
	}

	public static String getProperty(String key, String defaultValue) {
		String value = propertiesMap.get(key) == null ? "" : propertiesMap.get(key).toString();
		return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
	}

	public static int getTimeout() {
		try {
			return Integer.parseInt(getProperty("explicitWaitTimeout", "10"));
		} catch (NumberFormatException e) {
			LOGGER.error("Invalid explicitWaitTimeout value in config.properties. Using default 10 seconds.");
			return 10;
		}
	}

	public static String getSignupUrl() {
		return getProperty("signupUrl", "");
	}

	public static String getDocker() {
		String fromConfig = getProperty("runDocker", "");
		if (!fromConfig.isBlank()) {
			return fromConfig;
		}
		String fromSys = System.getProperty("runDocker", "");
		if (fromSys != null && !fromSys.isBlank()) {
			return fromSys;
		}
		String fromEnv = System.getenv("RUN_DOCKER");
		return fromEnv == null ? "" : fromEnv;
	}
}
