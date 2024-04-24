package io.mosip.esignet.api.validator;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.validator.AuthChallengeFactorFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;

@Component
public class AuthChallengeFactorFormatValidator implements ConstraintValidator<AuthChallengeFactorFormat, AuthChallenge> {

    private final String FORMAT_KEY_PREFIX = "mosip.esignet.auth-challenge.%s.format";

    @Autowired
    private Environment environment;

    @Override
    public boolean isValid(AuthChallenge authChallenge, ConstraintValidatorContext context) {
        if(StringUtils.hasText(authChallenge.getAuthFactorType()) && StringUtils.hasText(authChallenge.getFormat())) {
            String format = environment.getProperty(String.format(FORMAT_KEY_PREFIX, authChallenge.getAuthFactorType()),
                    String.class);
            if(!StringUtils.hasText(format)) {
            	context.disableDefaultConstraintViolation();
    			context.buildConstraintViolationWithTemplate(ErrorConstants.INVALID_AUTH_FACTOR_TYPE).addConstraintViolation();
    			return false;
            }
            return authChallenge.getFormat().equals(format);
        }
        else {
    		String errorMsg = !StringUtils.hasText(authChallenge.getAuthFactorType())? ErrorConstants.INVALID_AUTH_FACTOR_TYPE:ErrorConstants.INVALID_CHALLENGE_FORMAT;
    		context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(errorMsg).addConstraintViolation();
			return false;
        }
    }
}