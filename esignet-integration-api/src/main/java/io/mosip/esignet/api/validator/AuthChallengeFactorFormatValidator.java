package io.mosip.esignet.api.validator;

import io.mosip.esignet.api.dto.AuthChallenge;
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
                    String.class, "alpha-numeric");
            return authChallenge.getFormat().equals(format);
        }
        return false;
    }
}