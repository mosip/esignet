package utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResourceBundleLoader {

	private static final Map<String, String> resourceBundleMap = new HashMap<>();
	private static final Logger logger = Logger.getLogger(ResourceBundleLoader.class);
	private static volatile boolean loaded = false;
	private static String loadedLanguage = "";

	public static String get(String key) {
		String currentLang = System.getProperty("currentRunLanguage", "eng");
		if (!loaded || !currentLang.equalsIgnoreCase(loadedLanguage)) {
			synchronized (ResourceBundleLoader.class) {
				if (!loaded || !currentLang.equalsIgnoreCase(loadedLanguage)) {
					loadResourceBundleJson(currentLang);
					loaded = true;
					loadedLanguage = currentLang;
				}
			}
		}
		return resourceBundleMap.getOrDefault(key, "!!MISSING_KEY: " + key + "!!");
	}

	public static String getByIsoCode(String isoCode, String key) {
		Map<String, String> bundle = loadResourceBundleForIsoCode(isoCode);
		return bundle.getOrDefault(key, "!!MISSING_KEY: " + key + "!!");
	}

	private static void loadResourceBundleJson(String currentLang) {
		try {
			resourceBundleMap.clear();
			String twoLetterCode = LanguageUtil.getIsoLanguageCode(currentLang);
			if (twoLetterCode == null) {
				logger.warn("No ISO mapping found for language: " + currentLang + ", falling back to: " + currentLang);
				twoLetterCode = currentLang;
			}
			resourceBundleMap.putAll(loadResourceBundleForIsoCode(twoLetterCode));
			logger.info("Loaded resource bundle for language: " + currentLang);
		} catch (Exception e) {
			logger.error("Error loading resource bundle JSON for lang: " + currentLang, e);
		}
	}

	private static Map<String, String> loadResourceBundleForIsoCode(String isoCode) {
		Map<String, String> bundle = new HashMap<>();
		try {
			String url = EsignetConfigManager.getproperty("eSignetbaseurl") + "/locales/" + isoCode + ".json";
			String jsonContent = downloadJson(url);
			Map<String, Object> nestedMap = new ObjectMapper().readValue(jsonContent, new TypeReference<>() {
			});
			flatten(nestedMap, "", bundle);
			logger.info("Loaded resource bundle for ISO code: " + isoCode);
		} catch (Exception e) {
			logger.error("Error loading resource bundle JSON for ISO code: " + isoCode, e);
		}
		return bundle;
	}

	private static String downloadJson(String url) throws IOException {
		URI uri = URI.create(url);
		URLConnection connection = uri.toURL().openConnection();
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(10_000);
		try (InputStream in = connection.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@SuppressWarnings("unchecked")
	private static void flatten(Map<String, Object> source, String prefix, Map<String, String> target) {
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Map) {
				flatten((Map<String, Object>) value, key, target);
			} else {
				target.put(key, value.toString());
			}
		}
	}

	public static String getPrefixText(String key) {
		String value = get(key);
		if (value != null && value.startsWith("!!MISSING_KEY:")) {
			throw new IllegalStateException("Resource bundle missing key: " + key);
		}
		return value.split("\\{\\{currentID\\}\\}")[0].trim();
	}
}
