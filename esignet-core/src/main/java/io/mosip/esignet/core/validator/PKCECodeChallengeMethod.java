package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.ErrorConstants;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = PKCECodeChallengeMethodValidator.class)
@Documented
public @interface PKCECodeChallengeMethod {

    String message() default ErrorConstants.UNSUPPORTED_PKCE_CHALLENGE_METHOD;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
