package io.mosip.idp.core.validator;

import org.springframework.beans.factory.annotation.Value;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

public class OIDCClientAuthValidator implements ConstraintValidator<OIDCClientAuth, String> {

    @Value("#{${mosip.idp.supported.client.auth.methods}}")
    private List<String> supportedClientAuthMethods;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        return supportedClientAuthMethods.contains(value);
    }
}
