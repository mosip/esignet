package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.ErrorConstants;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = ClientAdditionalConfigValidator.class)
@Documented
public @interface ClientAdditionalConfig {
    String message() default ErrorConstants.INVALID_ADDITIONAL_CONFIG;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
