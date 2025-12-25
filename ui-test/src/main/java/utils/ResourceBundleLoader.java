package utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
		String currentLang = System.getProperty("currentRunLanguage", "en");
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

	private static void loadResourceBundleJson(String currentLang) {
		try {
			resourceBundleMap.clear();
			String twoLetterCode = LanguageUtil.getIsoLanguageCode(currentLang);
			String url = EsignetConfigManager.getproperty("localeUrl") + "/locales/" + twoLetterCode + ".json";

			String jsonContent = downloadJson(url);
			Map<String, Object> nestedMap = new ObjectMapper().readValue(jsonContent, new TypeReference<>() {
			});
			flatten(nestedMap, "", resourceBundleMap);

			logger.info("Loaded resource bundle for language: " + currentLang);

		} catch (Exception e) {
			logger.error("Error loading resource bundle JSON for lang: " + currentLang, e);
		}
	}

	private static String downloadJson(String url) throws IOException {
		URI uri = URI.create(url);
		try (InputStream in = uri.toURL().openStream()) {
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
}
