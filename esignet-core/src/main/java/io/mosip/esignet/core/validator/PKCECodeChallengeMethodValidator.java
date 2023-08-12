package io.mosip.esignet.core.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class PKCECodeChallengeMethodValidator implements ConstraintValidator<PKCECodeChallengeMethod, String> {

    @Value("#{${mosip.esignet.supported-pkce-methods}}")
    private List<String> supportedMethods;

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        return (!StringUtils.isEmpty(s) && !supportedMethods.contains("S256")) ? false : true;
    }
}
