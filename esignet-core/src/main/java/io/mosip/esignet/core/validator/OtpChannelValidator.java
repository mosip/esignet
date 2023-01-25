package io.mosip.esignet.core.validator;

import io.mosip.esignet.api.spi.Authenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;


@Component
public class OtpChannelValidator implements ConstraintValidator<OtpChannel, String> {

    @Autowired
    private Authenticator authenticationWrapper;

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        return authenticationWrapper.isSupportedOtpChannel(s);
    }
}
