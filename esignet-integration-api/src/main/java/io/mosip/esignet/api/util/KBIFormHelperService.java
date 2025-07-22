/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.esignet.api.exception.KBIFormException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KBIFormHelperService {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Reads and parses the JSON schema from a given resource URL.
     */
    public JsonNode fetchKBIFieldDetailsFromResource(String url) throws KBIFormException {
        try (InputStream inputStream = getResource(url)) {
            return objectMapper.readTree(inputStream);
        } catch (IOException e) {
            log.error("Error parsing the KBI form details: {}", e.getMessage(), e);
            throw new KBIFormException(ErrorConstants.KBI_SPEC_NOT_FOUND);
        }
    }

    /**
     * Loads the input stream of a given resource.
     */
    private InputStream getResource(String url) throws KBIFormException {
        try {
            Resource resource = resourceLoader.getResource(url);
            return resource.getInputStream();
        } catch (IOException e) {
            log.error("Failed to read resource from : {}", url, e);
            throw new KBIFormException(ErrorConstants.KBI_SPEC_NOT_FOUND);
        }
    }

    /**
     * Converts a list of flat field definitions into a structured KBI JSON schema.
     * <p>
     * Each field in the input list is expected to be a map with keys:
     * <ul>
     *     <li><code>id</code>: the field identifier (required)</li>
     *     <li><code>type</code>: the field type, e.g., "text" or "date"</li>
     *     <li><code>regex</code>: optional regular expression for validation</li>
     * </ul>
     *
     * The output JSON contains:
     * <ul>
     *     <li><code>schema</code>: an array of field objects with properties such as controlType, label, validators, etc.</li>
     *     <li><code>mandatoryLanguages</code>: an array specifying the mandatory languages (currently always ["eng"])</li>
     * </ul>
     *
     * <p><b>Example Input:</b></p>
     * <pre>{@code
     * [
     *   { "id": "user_name", "type": "text", "regex": "^[a-zA-Z0-9_]+$" },
     *   { "id": "date_of_birth", "type": "date", "regex": "" }
     * ]
     * }</pre>
     *
     * <p><b>Example Output:</b></p>
     * <pre>{@code
     * {
     *   "schema": [
     *     {
     *       "id": "user_name",
     *       "controlType": "textbox",
     *       "label": { "eng": "User Name" },
     *       "required": true,
     *       "validators": [
     *         { "type": "regex", "validator": "^[a-zA-Z0-9_]+$" }
     *       ]
     *     },
     *     {
     *       "id": "date_of_birth",
     *       "controlType": "date",
     *       "label": { "eng": "Date Of Birth" },
     *       "required": true,
     *       "validators": [],
     *       "type": "date"
     *     }
     *   ],
     *   "mandatoryLanguages": ["eng"]
     * }
     * }</pre>
     *
     * @param fieldList the list of field definition maps to migrate
     * @return the migrated JSON schema as a JsonNode
     * @throws KBIFormException if the input is empty or invalid, or migration fails
     */
    public JsonNode migrateKBIFieldDetails(List<Map<String, String>> fieldList) throws KBIFormException {
        if (CollectionUtils.isEmpty(fieldList)) {
            log.warn("KBI field details list is empty.");
            return null;
        }

        ArrayNode schemaArray = objectMapper.createArrayNode();

        try {
            for (Map<String, String> field : fieldList) {
                ObjectNode fieldNode = objectMapper.createObjectNode();

                String fieldId = field.get("id");
                String type = field.get("type");
                String regex = field.get("regex");

                if (fieldId == null || fieldId.trim().isEmpty()) {
                    log.error("Field Id is missing or empty: {}", field);
                    throw new KBIFormException(ErrorConstants.KBI_SCHEMA_PARSE_ERROR);
                }

                fieldNode.put("id", fieldId);
                fieldNode.put("controlType", "date".equalsIgnoreCase(type) ? "date" : "textbox");
                fieldNode.set("label", objectMapper.createObjectNode().put("eng", WordUtils.capitalizeFully(fieldId, '_', '-', '.')));
                fieldNode.put("required", true);

                ArrayNode validators = objectMapper.createArrayNode();
                if (regex != null && !regex.isEmpty()) {
                    ObjectNode validatorNode = objectMapper.createObjectNode();
                    validatorNode.put("type", "regex");
                    validatorNode.put("validator", regex);
                    validators.add(validatorNode);
                }
                fieldNode.set("validators", validators);

                if ("date".equalsIgnoreCase(type)) {
                    fieldNode.put("type", "date");
                }

                schemaArray.add(fieldNode);
            }

            ObjectNode finalSchema = objectMapper.createObjectNode();
            finalSchema.set("schema", schemaArray);
            finalSchema.putArray("mandatoryLanguages").add("eng");

            return finalSchema;
        } catch (Exception e) {
            log.error("Failed to generate KBI field schema from list", e);
            throw new KBIFormException(ErrorConstants.KBI_SCHEMA_PARSE_ERROR);
        }
    }
}
