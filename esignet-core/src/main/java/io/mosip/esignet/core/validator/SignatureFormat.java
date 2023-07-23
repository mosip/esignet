package io.mosip.esignet.core.validator;


import io.mosip.esignet.core.constants.ErrorConstants;

import javax.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(value = {ElementType.FIELD, TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = SignatureFormatValidator.class)
@Documented
public @interface SignatureFormat
{
    String message() default ErrorConstants.INVALID_SIGNATURE_FORMAT;

    Class<?>[] groups() default {};

    Class<?>[] payload() default {};
}