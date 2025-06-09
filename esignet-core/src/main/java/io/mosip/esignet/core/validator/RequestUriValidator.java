package io.mosip.esignet.core.validator;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class RequestUriValidator implements ConstraintValidator<RequestUri, String> {

    private boolean strictNo;

    @Override
    public void initialize(RequestUri constraintAnnotation) {
        this.strictNo = constraintAnnotation.strictNo();
    }

    @Override
    public boolean isValid(String requestUri, ConstraintValidatorContext context) {
        if (!strictNo) {
            return true;
        }
        return requestUri == null;
    }
}
