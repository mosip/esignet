package io.mosip.esignet.api.validator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.util.ErrorConstants;

import io.mosip.esignet.api.util.KBIFormHelperService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    @Autowired
    private KBIFormHelperService kbiFormHelperService;

    @Value("#{${mosip.esignet.authenticator.default.auth-factor.kbi.field-details}}")
    private List<Map<String, String>> fieldDetailList;

    @Value("${mosip.esignet.authenticator.default.auth-factor.kbi.individual-id-field}")
    private String idField;

    @Value("${mosip.esignet.authenticator.default.auth-factor.kbi.field-details-url:}")
    private String kbiFormDetailsUrl;

    private JsonNode fieldJson;

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
            byte[] decodedBytes = Base64.getUrlDecoder().decode(challenge);
            String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
            Map<String, Object> challengeMap = objectMapper.readValue(decodedString, new TypeReference<>() {});

            JsonNode schemaArray = getFieldJson().path("schema");
            for (JsonNode fieldNode : schemaArray) {
                String id = fieldNode.path("id").asText();
                String controlType = fieldNode.path("controlType").asText("textbox");
                boolean isRequired = fieldNode.path("required").asBoolean(false);

                if (("textbox".equalsIgnoreCase(controlType) || "date".equalsIgnoreCase(controlType)) && !id.equals(idField)) {
                    Object rawValue = challengeMap.get(id);
                    String value = null;

                    if (rawValue instanceof String) {
                        value = (String) rawValue;
                    } else if (rawValue instanceof List) {
                        try {
                            List<Map<String, String>> list = objectMapper.convertValue(rawValue, new TypeReference<List<Map<String, String>>>() {});
                            value = list.stream()
                                    .map(entry -> entry.getOrDefault("value", ""))
                                    .filter(StringUtils::hasText)
                                    .findFirst()
                                    .orElse("");
                        } catch (Exception e) {
                            log.warn("Invalid format for field '{}': expected list of maps", id);
                            value = "";
                        }
                    }

                    if (isRequired && !StringUtils.hasText(value)) {
                        log.warn("Validation failed: Required field '{}' is missing or empty", id);
                        return false;
                    }
                    if (StringUtils.hasText(value)) {
                        JsonNode maxLengthNode = fieldNode.get("maxLength");
                        if (maxLengthNode != null && maxLengthNode.isInt()) {
                            int maxLength = maxLengthNode.asInt();
                            if (value.length() > maxLength) {
                                log.warn("Validation failed: Value for field '{}' exceeds maxLength {} (actual length: {})", id, maxLength, value.length());
                                return false;
                            }
                        }

                        JsonNode validators = fieldNode.path("validators");
                        if (validators.isArray()) {
                            for (JsonNode validatorNode : validators) {
                                String validatorType = validatorNode.path("type").asText();
                                String regex = validatorNode.path("validator").asText();

                                if ("regex".equalsIgnoreCase(validatorType) && StringUtils.hasText(regex)) {
                                    if (!Pattern.matches(regex, value)) {
                                        log.warn("Validation failed: Value '{}' for field '{}' does not match regex '{}'", value, id, regex);
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating challenge", e);
            return false;
        }
    }

    private JsonNode getFieldJson() {
        try {
            fieldJson = StringUtils.hasText(kbiFormDetailsUrl)
                    ? kbiFormHelperService.fetchKBIFieldDetailsFromResource(kbiFormDetailsUrl)
                    : kbiFormHelperService.migrateKBIFieldDetails(fieldDetailList);
        } catch (Exception e) {
            log.error("Error loading KBI form details: {}", kbiFormDetailsUrl, e);
        }
        return fieldJson;
    }

}
