package io.mosip.idp.validators;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.mosip.idp.util.ErrorConstants.INVALID_GRANT_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD })
@Retention(RUNTIME)
@Constraint(validatedBy = OIDCGrantTypeValidator.class)
@Documented
public @interface OIDCGrantType {

    String message() default INVALID_GRANT_TYPE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
