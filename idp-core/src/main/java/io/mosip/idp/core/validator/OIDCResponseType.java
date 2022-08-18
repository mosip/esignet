package io.mosip.idp.core.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.mosip.idp.core.util.ErrorConstants.INVALID_RESPONSE_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD })
@Retention(RUNTIME)
@Constraint(validatedBy = OIDCResponseTypeValidator.class)
@Documented
public @interface OIDCResponseType {

    String message() default INVALID_RESPONSE_TYPE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
