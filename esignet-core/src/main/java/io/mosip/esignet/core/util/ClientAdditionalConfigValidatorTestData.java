package io.mosip.esignet.core.util;

import java.util.*;

public class ClientAdditionalConfigValidatorTestData {

    public static Map<String, Object> getValidAdditionalConfig() {
        Map<String, Object> validAdditionalConfig = new HashMap<>();
        validAdditionalConfig.put("userinfo_response_type", "JWS");
        validAdditionalConfig.put("purpose", Map.ofEntries(
                Map.entry("type", ""),
                Map.entry("title", ""),
                Map.entry("subTitle", "")
        ));
        validAdditionalConfig.put("signup_banner_required", true);
        validAdditionalConfig.put("forgot_pwd_link_required", true);
        validAdditionalConfig.put("consent_expire_in_days", 1);
        return validAdditionalConfig;
    }

    public static List<Map<String, Object>> getInvalidAdditionalConfigs() {
        List<Map<String, Object>> invalidAdditionalConfigs = new ArrayList<>();

        invalidAdditionalConfigs.add(null);

        Map<String, Object> additionalConfig = getValidAdditionalConfig();
        additionalConfig.put("userinfo_response_type", "ABC");
        invalidAdditionalConfigs.add(additionalConfig);

        additionalConfig = getValidAdditionalConfig();
        additionalConfig.put("purpose", Collections.emptyMap());
        invalidAdditionalConfigs.add(additionalConfig);

        additionalConfig = getValidAdditionalConfig();
        additionalConfig.put("purpose", Map.ofEntries(
                Map.entry("type", ""),
                Map.entry("title", 1),   //anything other than string
                Map.entry("subTitle", "")
        ));
        invalidAdditionalConfigs.add(additionalConfig);

        additionalConfig = getValidAdditionalConfig();
        additionalConfig.put("signup_banner_required", 1); // anything other than boolean
        invalidAdditionalConfigs.add(additionalConfig);

        additionalConfig = getValidAdditionalConfig();
        additionalConfig.put("forgot_pwd_link_required", 1); // anything other than boolean
        invalidAdditionalConfigs.add(additionalConfig);

        additionalConfig = getValidAdditionalConfig();
        additionalConfig.put("consent_expire_in_days", ""); // anything other than number
        invalidAdditionalConfigs.add(additionalConfig);

        return invalidAdditionalConfigs;
    }

}
