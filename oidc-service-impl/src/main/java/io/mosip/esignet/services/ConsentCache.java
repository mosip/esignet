package io.mosip.esignet.services;

import io.mosip.esignet.core.dto.UserConsent;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConsentCache {
    private ConsentCache() {
    }

    private static final Map<String, UserConsent> USER_CONSENT_MAP = new HashMap<>();

    public static void addUserConsent(String identifier, UserConsent consent){
        USER_CONSENT_MAP.put(identifier,consent);
    }

    public static void removeUserConsent(String identifier) {
        USER_CONSENT_MAP.remove(identifier);
    }

    public static UserConsent getUserConsent(String identifier){
        return USER_CONSENT_MAP.get(identifier);
    }

    public static void updateUserConsent(String identifier, UserConsent consent){
        USER_CONSENT_MAP.put(identifier,consent);
    }
}
