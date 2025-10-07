package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanguageUtil {

    private static final Map<String, String> languagesMap = new HashMap<>();
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

    private static String downloadJson(String url) throws IOException {
        URI uri = URI.create(url); // preferred over new URL(String)
        try (InputStream in = uri.toURL().openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
