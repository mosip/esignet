package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.dto.PushedAuthorizationRequest;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class RequestUriValidator implements ConstraintValidator<RequestUri, PushedAuthorizationRequest> {

    private boolean strictNo;

    @Override
    public void initialize(RequestUri constraintAnnotation) {
        this.strictNo = constraintAnnotation.strictNo();
    }

    @Override
    public boolean isValid(PushedAuthorizationRequest request, ConstraintValidatorContext context) {
        if (request == null || !strictNo) {
            return true;
        }
        return request.getRequest_uri() == null;
    }
}
