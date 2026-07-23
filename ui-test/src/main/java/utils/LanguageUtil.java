package utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LanguageUtil {

    public static final Map<String, String> languagesMap = new HashMap<>();
    private static final Map<String, String> langCodeMappingMap = new HashMap<>();
    public static List<String> supportedLanguages = new ArrayList<>();
    private static final Logger logger = Logger.getLogger(LanguageUtil.class);

    static {
        try {
            // URL from config
            String url = EsignetConfigManager.getproperty("localeUrl") + "locales/default.json";

            // Download JSON content as String
            String jsonContent = downloadJson(url);

            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonContent);

            // Populate languages_2Letters map
            rootNode.get("languages_2Letters").fields()
                    .forEachRemaining(entry -> languagesMap.put(entry.getKey(), entry.getValue().asText()));

            // Populate langCodeMapping map
            rootNode.get("langCodeMapping").fields()
                    .forEachRemaining(entry -> langCodeMappingMap.put(entry.getKey(), entry.getValue().asText()));

            // Populate keys list
            supportedLanguages = new ArrayList<>(langCodeMappingMap.keySet());

        } catch (IOException e) {
            logger.error("Error language locale JSON", e);
        }
    }

    /**
     * Returns the display name for a given language code.
     * If code is not found, returns the code itself.
     */
    public static String getDisplayName(String code) {
        String twoLetter = langCodeMappingMap.getOrDefault(code, code);
        return languagesMap.getOrDefault(twoLetter, code);
    }

    /**
     * Returns the two-letter code for a given language code.
     * If code is not found, returns the code itself.
     */
    public static String getIsoLanguageCode(String code) {
        return langCodeMappingMap.get(code);
    }

    /**
     * Resolves a browser-reported locale (e.g. {@code en-US}) to a supported two-letter code.
     */
    public static String resolveFromBrowserLocale(String navigatorLanguage) {
        if (navigatorLanguage == null || navigatorLanguage.isBlank()) {
            return null;
        }
        String primary = navigatorLanguage.split("-")[0].toLowerCase();
        if (languagesMap.containsKey(primary)) {
            return primary;
        }
        String mapped = langCodeMappingMap.get(navigatorLanguage.toLowerCase());
        if (mapped != null) {
            return mapped;
        }
        mapped = langCodeMappingMap.get(primary);
        return mapped != null ? mapped : primary;
    }

    public static boolean matchesLanguageCode(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        String resolvedActual = resolveFromBrowserLocale(actual);
        String resolvedExpected = resolveFromBrowserLocale(expected);
        return resolvedActual != null && resolvedActual.equalsIgnoreCase(resolvedExpected);
    }

    /**
     * Neutral browser locale so navigator.language does not match any supported IDP language (TC_14).
     */
    public static String getNeutralBrowserLocale() {
        String locale = EsignetConfigManager.getproperty("defaultLangTestNeutralLocale");
        return (locale != null && !locale.isBlank()) ? locale.trim() : "xx";
    }

    public static String fetchDefaultLangFromEnvConfig() {
        String url = EsignetConfigManager.getproperty("eSignetbaseurl") + "/env-config.js";
        try {
            String content = downloadJson(url);
            Matcher matcher = Pattern.compile("DEFAULT_LANG\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(content);
            if (!matcher.find()) {
                throw new IllegalStateException("DEFAULT_LANG not found in env-config.js at " + url);
            }
            String defaultLang = matcher.group(1);
            logger.info("DEFAULT_LANG from env-config.js: " + defaultLang);
            return defaultLang;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch env-config.js from " + url, e);
        }
    }

    /**
     * Resolves {@code DEFAULT_LANG} from env-config (2- or 3-letter) to a supported two-letter code.
     */
    public static String resolveDefaultLangToIsoCode(String defaultLang) {
        if (defaultLang == null || defaultLang.isBlank()) {
            return null;
        }
        String normalized = defaultLang.trim().toLowerCase();
        if (languagesMap.containsKey(normalized)) {
            return normalized;
        }
        if (langCodeMappingMap.containsKey(normalized)) {
            return langCodeMappingMap.get(normalized);
        }
        return normalized.split("-")[0];
    }

    public static String getDisplayNameFromIso(String isoCode) {
        return languagesMap.getOrDefault(isoCode, isoCode);
    }

    public static boolean isSupportedBrowserLocale(String navigatorLanguage) {
        String resolved = resolveFromBrowserLocale(navigatorLanguage);
        return resolved != null && languagesMap.containsKey(resolved);
    }

    private static String downloadJson(String url) throws IOException {
        URI uri = URI.create(url); // preferred over new URL(String)
        try (InputStream in = uri.toURL().openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
