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
		// Load scope specific properties
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

			// Convert Properties to Map and add to moduleSpecificPropertiesMap
			for (String key : configProps.stringPropertyNames()) {
				moduleSpecificPropertiesMap.put(key, configProps.getProperty(key));
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
		// Add module specific properties as well.
		init(moduleSpecificPropertiesMap);
	}

	private static void loadPropertiesFile(String fileName, Map<String, Object> propMap) {
		try (InputStream inputStream = EsignetConfigManager.class.getClassLoader().getResourceAsStream(fileName)) {
			if (inputStream == null) {
				LOGGER.error(fileName + " resource not found in classpath");
				throw new FileNotFoundException(fileName + " not found");
			}
			Properties props = new Properties();
			props.load(inputStream);
			LOGGER.info(fileName + " loaded successfully.");

			for (String key : props.stringPropertyNames()) {
				propMap.put(key, props.getProperty(key));
			}
		} catch (IOException e) {
			LOGGER.error("Failed to load " + fileName, e);
			throw new RuntimeException("Failed to load " + fileName, e);
		}
	}

	public static String getDbUrl() {
		return getProperty("db-server-es", "");
	}

	public static String getDbUser() {
		return getProperty("db-su-user", "");
	}

	public static String getDbPassword() {
		return getProperty("postgres-password", "");
	}

	public static String getDbSchema() {
		return getProperty("es_db_schema", "");
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

	public static String getSignupPortalUrl() {
		return getProperty("signup.portal.url", "");
	}

	public static String getSmtpUrl() {
		return getProperty("smtp.url", "");
	}

	public static String getHealthPortalUrl() {
		return getProperty("baseurl", "");
	}
}
