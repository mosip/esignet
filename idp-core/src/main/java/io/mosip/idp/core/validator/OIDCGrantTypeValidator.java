package io.mosip.idp.core.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class OIDCGrantTypeValidator implements ConstraintValidator<OIDCGrantType, String> {

    @Value("#{${mosip.idp.supported.grant.types}}")
    private List<String> supportedGrantTypes;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        return supportedGrantTypes.contains(value);
    }
}
