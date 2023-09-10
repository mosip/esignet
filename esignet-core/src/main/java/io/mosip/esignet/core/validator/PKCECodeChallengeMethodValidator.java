package io.mosip.esignet.core.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class PKCECodeChallengeMethodValidator implements ConstraintValidator<PKCECodeChallengeMethod, String> {

    @Value("#{${mosip.esignet.supported-pkce-methods}}")
    private List<String> supportedMethods;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        return value==null || supportedMethods.contains(value);
    }
}
