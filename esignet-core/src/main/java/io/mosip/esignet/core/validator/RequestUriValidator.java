package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.Constants;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RequestUriValidator implements ConstraintValidator<RequestUri, String> {

    @Override
    public boolean isValid(String requestUri, ConstraintValidatorContext context) {
        return requestUri != null && requestUri.startsWith(Constants.PAR_REQUEST_URI_PREFIX);
    }
}
