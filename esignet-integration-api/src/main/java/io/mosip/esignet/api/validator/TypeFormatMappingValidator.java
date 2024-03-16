package io.mosip.esignet.api.validator;

import io.mosip.esignet.api.dto.AuthChallenge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;

@Component
public class TypeFormatMappingValidator implements ConstraintValidator<TypeFormatMapping, AuthChallenge> {

    @Value("#{${mosip.esignet.supported-formats}}")
    private Map<String, Object> supportedFormats;

    @Override
    public void initialize(TypeFormatMapping constraintAnnotation) {
    }

    @Override
    public boolean isValid(AuthChallenge authChallenge, ConstraintValidatorContext context) {
        Object supportedFormatType = supportedFormats.get(authChallenge.getAuthFactorType());
        if (supportedFormatType != null ) {
            String supportedFormat = (String) supportedFormatType;
            return supportedFormat.equals(authChallenge.getFormat());
        }
        return false;
    }
}