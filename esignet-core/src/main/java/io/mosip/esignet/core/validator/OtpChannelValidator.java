package io.mosip.esignet.core.validator;

import io.mosip.esignet.api.spi.Authenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


@Component
public class OtpChannelValidator implements ConstraintValidator<OtpChannel, String> {

    @Autowired
    private Authenticator authenticationWrapper;

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        return authenticationWrapper.isSupportedOtpChannel(s);
    }
}
