package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.dto.PushedAuthorizationRequest;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class NoRequestUriValidator implements ConstraintValidator<NoRequestUri, PushedAuthorizationRequest> {

    @Override
    public boolean isValid(PushedAuthorizationRequest request, ConstraintValidatorContext context) {
        return request == null || request.getRequest_uri() == null;
    }
}
