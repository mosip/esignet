package io.mosip.esignet.core.validator;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class SignatureFormatValidator implements ConstraintValidator<SignatureFormat,String> {

    @Override
    public boolean isValid(String signature, ConstraintValidatorContext context) {
        if(signature==null || signature.isEmpty())return false;
        String jws[]=signature.split("\\.");
        if(jws.length!=2)return false;
        return true;
    }
}
