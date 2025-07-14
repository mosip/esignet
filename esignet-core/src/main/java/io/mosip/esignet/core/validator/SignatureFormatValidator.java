package io.mosip.esignet.core.validator;

import org.springframework.util.StringUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SignatureFormatValidator implements ConstraintValidator<SignatureFormat,String> {

    @Override
    public boolean isValid(String signature, ConstraintValidatorContext context) {
        return  (StringUtils.isEmpty(signature) || signature.split("\\.").length!=2) ? false : true;
    }
}
