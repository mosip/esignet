package io.mosip.esignet.api.validator;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.util.ErrorConstants;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class AuthChallengeLengthValidator implements ConstraintValidator<AuthChallengeLength, AuthChallenge> {

    private final String MIN_LENGTH_KEY_PREFIX = "mosip.esignet.auth-challenge.%s.min-length";
    private final String MAX_LENGTH_KEY_PREFIX = "mosip.esignet.auth-challenge.%s.max-length";

    @Autowired
    private Environment environment;

    @Override
    public boolean isValid(AuthChallenge authChallenge, ConstraintValidatorContext context) {
        if(!StringUtils.hasText(authChallenge.getChallenge())) {
        	context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(ErrorConstants.INVALID_CHALLENGE).addConstraintViolation();
			return false;
        }
            String authFactor = StringUtils.hasText(authChallenge.getAuthFactorType())?authChallenge.getAuthFactorType():"DEFAULT";
            int min = environment.getProperty(String.format(MIN_LENGTH_KEY_PREFIX, authFactor), Integer.TYPE, 50);
            int max = environment.getProperty(String.format(MAX_LENGTH_KEY_PREFIX, authFactor), Integer.TYPE, 50);
            int length = authChallenge.getChallenge().length();
            return length>=min && length<=max;
    }
}
