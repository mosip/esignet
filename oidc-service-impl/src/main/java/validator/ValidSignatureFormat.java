package validator;


import javax.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ElementType.LOCAL_VARIABLE, TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = SignatureFormatValidator.class)
@Documented
public @interface ValidSignatureFormat {

        String message() default "Invalid Signature Format";

        Class<?>[] groups() default {};

        Class<? extends javax.validation.Payload>[] payload() default {};
}
