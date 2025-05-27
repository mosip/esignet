package io.mosip.esignet.api.validator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.util.ErrorConstants;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.text.WordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Component
public class AuthChallengeFactorFormatValidator implements ConstraintValidator<AuthChallengeFactorFormat, AuthChallenge> {

    private final String FORMAT_KEY_PREFIX = "mosip.esignet.auth-challenge.%s.format";
    private final String MIN_LENGTH_KEY_PREFIX = "mosip.esignet.auth-challenge.%s.min-length";
    private final String MAX_LENGTH_KEY_PREFIX = "mosip.esignet.auth-challenge.%s.max-length";
    
    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("#{${mosip.esignet.authenticator.default.auth-factor.kbi.field-details}}")
    private List<Map<String, String>> fieldDetailList;

    @Value("${mosip.esignet.authenticator.default.auth-factor.kbi.individual-id-field}")
    private String idField;

    @Value("${mosip.esignet.authenticator.default.auth-factor.kbi.field-details-url:}")
    private String kbiFormDetailsUrl;

    @Autowired
    private ResourceLoader resourceLoader;

    private JsonNode fieldJson;

    @PostConstruct
    public void init() {
        try {
            fieldJson = StringUtils.hasText(kbiFormDetailsUrl)
                    ? fetchKBIFieldDetailsFromResource(kbiFormDetailsUrl)
                    : migrateKBIFieldDetails(fieldDetailList);
        } catch (Exception e) {
            log.error("Error loading KBI form details: {}", kbiFormDetailsUrl, e);
        }
    }

    @Override
    public boolean isValid(AuthChallenge authChallenge, ConstraintValidatorContext context) {
    	String authFactor = authChallenge.getAuthFactorType();
        String format = environment.getProperty(String.format(FORMAT_KEY_PREFIX, authFactor),
                String.class);
        if( !StringUtils.hasText(authFactor) || !StringUtils.hasText(format) || !authChallenge.getAuthFactorType().equals(authFactor.toUpperCase()) ) {
        	context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(ErrorConstants.INVALID_AUTH_FACTOR_TYPE).addConstraintViolation();
			return false;
        }
        if( !StringUtils.hasText(authChallenge.getFormat()) || !authChallenge.getFormat().equals(format) ) {
        	context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(ErrorConstants.INVALID_CHALLENGE_FORMAT).addConstraintViolation();
        	return false;
        }
        int min = environment.getProperty(String.format(MIN_LENGTH_KEY_PREFIX, authFactor), Integer.TYPE, 50);
        int max = environment.getProperty(String.format(MAX_LENGTH_KEY_PREFIX, authFactor), Integer.TYPE, 50);
        String challenge = authChallenge.getChallenge();
        int length = StringUtils.hasText(challenge) ? challenge.length() : 0;
        if (!(length >= min && length <= max)) {
            return false;
        }

        if (authFactor.equals("KBI")) {
            return validateChallenge(authChallenge.getChallenge());
        }
        return true;
    }

    private boolean validateChallenge(String challenge) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(challenge);
            String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
            Map<String, String> challengeMap = objectMapper.readValue(decodedString, new TypeReference<>() {});

            JsonNode schemaArray = fieldJson.path("schema");
            if (!schemaArray.isArray()) return false;

            for (JsonNode fieldNode : schemaArray) {
                String id = fieldNode.path("id").asText();
                String type = fieldNode.path("type").asText("text");

                if ("text".equalsIgnoreCase(type) && !id.equals(idField)) {
                    String value = challengeMap.get(id);
                    if (!StringUtils.hasText(value)) return false;

                    int maxLength = fieldNode.path("maxLength").asInt(50);
                    if (value.length() > maxLength) return false;

                    JsonNode validators = fieldNode.path("validators");
                    if (validators.isArray() && validators.size() > 0) {
                        String regex = validators.get(0).path("validator").asText();
                        if (!Pattern.matches(regex, value)) return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating KBI challenge", e);
            return false;
        }
    }

    private JsonNode fetchKBIFieldDetailsFromResource(String url) {
        try (InputStream resp = getResource(url)) {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(resp);
        } catch (IOException e) {
            log.error("Error parsing the KBI form details: {}", e.getMessage(), e);
        }
        return null;
    }

    private InputStream getResource(String url) {
        try {
            Resource resource = resourceLoader.getResource(url);
            return resource.getInputStream();
        } catch (IOException e) {
            log.error("Failed to read resource from : {}", url, e);
            return null;
        }
    }

    private JsonNode migrateKBIFieldDetails(List<Map<String, String>> fieldList) {
        if (fieldList == null || fieldList.isEmpty()) {
            log.warn("KBI field details list is empty.");
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode schemaArray = mapper.createArrayNode();

        try {
            for (Map<String, String> field : fieldList) {
                ObjectNode fieldNode = mapper.createObjectNode();


                String fieldId = field.get("id");
                String type = field.get("type");
                String regex = field.get("regex");

                if (fieldId == null || fieldId.trim().isEmpty()) {
                    log.error("Field Id is missing or empty: {}", field);
                }

                fieldNode.put("id", fieldId);
                fieldNode.put("controlType", "date".equalsIgnoreCase(type) ? "date" : "textbox");
                fieldNode.set("label", mapper.createObjectNode().put("eng", WordUtils.capitalizeFully(fieldId, '_', '-', '.')));
                fieldNode.put("required", true);

                ArrayNode validators = mapper.createArrayNode();
                if (regex != null && !regex.isEmpty()) {
                    ObjectNode validatorNode = mapper.createObjectNode();
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

            ObjectNode finalSchema = mapper.createObjectNode();
            finalSchema.set("schema", schemaArray);
            finalSchema.putArray("mandatoryLanguages").add("eng");

            return finalSchema;
        } catch (Exception e) {
            log.error("Failed to generate KBI field schema from list", e);
            return null;
        }
    }

}
