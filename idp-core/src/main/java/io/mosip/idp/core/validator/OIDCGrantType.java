package io.mosip.idp.core.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.mosip.idp.core.util.ErrorConstants.INVALID_GRANT_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = OIDCGrantTypeValidator.class)
@Documented
public @interface OIDCGrantType {

    String message() default INVALID_GRANT_TYPE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
