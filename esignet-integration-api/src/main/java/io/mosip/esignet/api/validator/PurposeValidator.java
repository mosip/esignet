package io.mosip.esignet.api.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PurposeValidator implements ConstraintValidator<Purpose, String> {

    @Value("${mosip.esignet.claim-detail.purpose.min-length}")
    private int minLength;

    @Value("${mosip.esignet.claim-detail.purpose.max-length}")
    private int maxLength;

    @Override
    public boolean isValid(String purpose, ConstraintValidatorContext constraintValidatorContext) {
        if(purpose==null) return true;
        int length = StringUtils.hasText(purpose) ? purpose.length() : 0;
        return length >= minLength && length <= maxLength;
    }
}
