package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.Constants;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class RequestUriValidator implements ConstraintValidator<RequestUri, String> {

    @Override
    public void initialize(RequestUri constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String requestUri, ConstraintValidatorContext context) {
        return requestUri != null && requestUri.startsWith(Constants.PAR_REQUEST_URI_PREFIX);
    }
}
