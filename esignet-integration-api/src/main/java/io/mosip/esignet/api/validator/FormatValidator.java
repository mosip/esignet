package io.mosip.esignet.api.validator;

import io.mosip.esignet.api.dto.AuthChallenge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.Map;


@Component
public class FormatValidator implements ConstraintValidator<Format, AuthChallenge> {
    @Value("#{${mosip.esignet.supported-formats}}")
    private Map<String, Object> supportedFormats;

    @Override
    public void initialize(Format constraintAnnotation) {
    }

    @Override
    public boolean isValid(AuthChallenge authChallenge, ConstraintValidatorContext context) {
        Object supportedFormatType = supportedFormats.get(authChallenge.getAuthFactorType());
        if(supportedFormatType instanceof List) {
            List<String> supportedFormatsList = (List<String>) supportedFormatType;
            if (!supportedFormatsList.contains(authChallenge.getFormat())) {
                return false;
            }
        } else if (supportedFormatType instanceof String) {
            String supportedFormat = (String) supportedFormatType;
            if (!supportedFormat.equals(authChallenge.getFormat())) {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

}