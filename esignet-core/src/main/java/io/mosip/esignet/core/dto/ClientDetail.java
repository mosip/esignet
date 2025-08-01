/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;
import java.io.Serializable;
import java.util.Map;

@Data
public class ClientDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private Map<String, String> name;
    private String rpId;
    private String logoUri;
    private List<String> redirectUris;
    private String publicKey;
    private List<String> claims;
    private List<String> acrValues;
    private String status;
    private List<String> grantTypes;
    private List<String> clientAuthMethods;
    private JsonNode additionalConfig;

    public <T> T getAdditionalConfig(String key, T defaultValue) {
        if (this.additionalConfig == null || key == null || !this.additionalConfig.has(key)) {
            return defaultValue;
        }
        JsonNode valueNode = this.additionalConfig.get(key);
        if (defaultValue instanceof String) {
            return (T) (valueNode.isTextual() ? valueNode.asText() : defaultValue);
        } else if (defaultValue instanceof Integer) {
            return (T) (valueNode.isInt() ? Integer.valueOf(valueNode.asInt()) : defaultValue);
        } else if (defaultValue instanceof Long) {
            return (T) (valueNode.isLong() ? Long.valueOf(valueNode.asLong()) : defaultValue);
        } else if (defaultValue instanceof Double) {
            return (T) (valueNode.isDouble() ? Double.valueOf(valueNode.asDouble()) : defaultValue);
        } else if (defaultValue instanceof Boolean) {
            return (T) (valueNode.isBoolean() ? Boolean.valueOf(valueNode.asBoolean()) : defaultValue);
        }
        return defaultValue;
    }

    public static String DPOP_CONFIG_KEY = "dpop_bound_access_tokens";
}
