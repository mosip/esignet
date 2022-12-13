/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.mosip.idp.core.util.ErrorConstants.UNSUPPORTED_ID_FORMAT;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = IdFormatValidator.class)
@Documented
public @interface IdFormat {

    String DEFAULT_SUPPORTED_REGEX = "\\S*";

    String formatRegexProperty() default DEFAULT_SUPPORTED_REGEX;

    String message() default UNSUPPORTED_ID_FORMAT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
