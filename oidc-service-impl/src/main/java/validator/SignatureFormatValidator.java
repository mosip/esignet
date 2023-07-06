package validator;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class SignatureFormatValidator implements ConstraintValidator<ValidSignatureFormat,String> {

    @Override
    public boolean isValid(String signature, ConstraintValidatorContext context) {
        String jws[]=signature.split("\\.");
        return jws.length==2;
    }
}
