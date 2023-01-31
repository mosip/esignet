/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
@Constraint(validatedBy = OIDCClaimValidator.class)
@Documented
public @interface OIDCClaim {

    String message() default ErrorConstants.INVALID_CLAIM;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
