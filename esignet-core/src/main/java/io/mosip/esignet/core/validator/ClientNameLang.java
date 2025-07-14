package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.ErrorConstants;

import jakarta.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = ClientNameLangValidator.class)
@Documented
public @interface ClientNameLang {
    String message() default ErrorConstants.INVALID_CLIENT_NAME_MAP_KEY;

    Class<?>[] groups() default {};

    Class<?>[] payload() default {};
}
