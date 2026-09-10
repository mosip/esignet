package utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final Map<String, String> FRONTEND_DISPLAY_NAMES = Map.of(
            "eng", "English",
            "khm", "ខ្មែរ",
            "hin", "हिन्दी",
            "fra", "français",
            "spa", "español");

    private static final Map<String, String> FRONTEND_DISPLAY_NAMES_BY_ISO2 = Map.of(
            "en", "English",
            "km", "ខ្មែរ",
            "hi", "हिन्दी",
            "fr", "français",
            "es", "español");

    private static final Map<String, String> FRONTEND_ISO2_BY_CODE = Map.of(
            "eng", "en",
            "khm", "km",
            "hin", "hi",
            "fra", "fr",
            "spa", "es");

    static {
        try {

            String localeUrl = EsignetConfigManager.getproperty("localeUrl");
            String url = (localeUrl.endsWith("/") ? localeUrl : localeUrl + "/") + "locales/default.json";

            String jsonContent = downloadJson(url);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonContent);

            rootNode.get("languages_2Letters").fields()
                    .forEachRemaining(entry -> languagesMap.put(entry.getKey(), entry.getValue().asText()));

            rootNode.get("langCodeMapping").fields()
                    .forEachRemaining(entry -> langCodeMappingMap.put(entry.getKey(), entry.getValue().asText()));

            supportedLanguages = new ArrayList<>(langCodeMappingMap.keySet());

        } catch (Exception e) {
            logger.error("Error language locale JSON", e);

            String runLanguage = EsignetConfigManager.getproperty("runLanguage");
            if (runLanguage != null && !runLanguage.isBlank()) {
                supportedLanguages = Arrays.stream(runLanguage.split(","))
                        .map(String::trim)
                        .filter(lang -> !lang.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
                logger.warn("Falling back to config's runLanguage for supported languages: " + supportedLanguages);
            }
        }
    }

    public static String getDisplayName(String code) {
        String twoLetter = langCodeMappingMap.getOrDefault(code, code);
        String fromApi = languagesMap.get(twoLetter);
        if (fromApi != null) {
            return fromApi;
        }
        String key = code == null ? "" : code.trim().toLowerCase();
        return FRONTEND_DISPLAY_NAMES.getOrDefault(key, code);
    }

    public static String getIsoLanguageCode(String code) {
        String mapped = langCodeMappingMap.get(code);
        if (mapped != null) {
            return mapped;
        }
        String key = code == null ? "" : code.trim().toLowerCase();
        return FRONTEND_ISO2_BY_CODE.get(key);
    }

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
        if (mapped != null) {
            return mapped;
        }
        return FRONTEND_ISO2_BY_CODE.getOrDefault(primary, primary);
    }

    public static boolean matchesLanguageCode(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        String resolvedActual = resolveFromBrowserLocale(actual);
        String resolvedExpected = resolveFromBrowserLocale(expected);
        return resolvedActual != null && resolvedActual.equalsIgnoreCase(resolvedExpected);
    }

    public static String getNeutralBrowserLocale() {
        String locale = EsignetConfigManager.getproperty("defaultLangTestNeutralLocale");
        return (locale != null && !locale.isBlank()) ? locale.trim() : "xx";
    }

    public static boolean isNeutralStoredLanguage(String storedLanguage) {
        if (storedLanguage == null || storedLanguage.isBlank()) {
            return false;
        }
        String neutral = getNeutralBrowserLocale();
        return matchesLanguageCode(storedLanguage, neutral) || "xx".equalsIgnoreCase(storedLanguage.trim());
    }

    public static String fetchDefaultLangFromEnvConfig() {
        String baseUrl = EsignetConfigManager.getProperty("eSignetbaseurl", "").trim();
        if (baseUrl.isEmpty()) {
            throw new IllegalStateException("eSignetbaseurl is not configured; cannot read DEFAULT_LANG");
        }
        String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "env-config.js";
        try {
            String content = downloadJson(url);
            Matcher matcher = Pattern.compile("DEFAULT_LANG\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(content);
            if (!matcher.find()) {
                throw new IllegalStateException("DEFAULT_LANG not found in env-config.js at " + url);
            }
            String defaultLang = matcher.group(1);
            logger.info("DEFAULT_LANG from env-config.js: " + defaultLang);
            return defaultLang;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to fetch env-config.js from " + url, e);
        }
    }

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
        if (FRONTEND_ISO2_BY_CODE.containsKey(normalized)) {
            return FRONTEND_ISO2_BY_CODE.get(normalized);
        }
        return normalized.split("-")[0];
    }

    public static String getDisplayNameFromIso(String isoCode) {
        String fromApi = languagesMap.get(isoCode);
        if (fromApi != null) {
            return fromApi;
        }

        String key = isoCode == null ? "" : isoCode.trim().toLowerCase();
        return FRONTEND_DISPLAY_NAMES_BY_ISO2.getOrDefault(key, isoCode);
    }

    public static boolean isSupportedBrowserLocale(String navigatorLanguage) {
        String resolved = resolveFromBrowserLocale(navigatorLanguage);
        return resolved != null
                && (languagesMap.containsKey(resolved) || FRONTEND_DISPLAY_NAMES_BY_ISO2.containsKey(resolved));
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
}
