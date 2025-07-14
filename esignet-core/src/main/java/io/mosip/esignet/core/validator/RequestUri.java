package io.mosip.esignet.core.validator;
import io.mosip.esignet.core.constants.ErrorConstants;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = RequestUriValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestUri {

    String message() default ErrorConstants.INVALID_REQUEST;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
