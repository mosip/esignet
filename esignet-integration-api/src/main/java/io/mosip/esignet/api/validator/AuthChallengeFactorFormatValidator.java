package io.mosip.esignet.api.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.util.ErrorConstants;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    @Value("#{${mosip.esignet.authenticator.default.auth-factor.kba.field-details}}")
    private List<Map<String, String>> fieldDetailList;

    @Override
    public boolean isValid(AuthChallenge authChallenge, ConstraintValidatorContext context) {
    	String authFactor = authChallenge.getAuthFactorType();
        String format = environment.getProperty(String.format(FORMAT_KEY_PREFIX, authFactor),
                String.class);
        if( !StringUtils.hasText(authFactor) || !StringUtils.hasText(format)) {
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
        if (authFactor.equals("KBA")) {
            return validateChallenge(authChallenge.getChallenge());
        }
        return true;
    }

    private boolean validateChallenge(String challenge) {
        byte[] decodedBytes = Base64.getDecoder().decode(challenge);
        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
        Map<String, String> challengeMap;
        try {
            challengeMap = objectMapper.readValue(decodedString, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to parse the input challenge", e);
            return false;
        }
        for (Map<String, String> fieldDetail : fieldDetailList) {
            String fieldId = fieldDetail.get("id");
            if(fieldDetail.containsKey("regex") && challengeMap.containsKey(fieldId) &&
                    StringUtils.hasText(challengeMap.get(fieldId))) {
                Pattern pattern = Pattern.compile(fieldDetail.get("regex"));
                return pattern.matcher(challengeMap.get(fieldId)).matches();
            }
        }
        return true;
    }
}