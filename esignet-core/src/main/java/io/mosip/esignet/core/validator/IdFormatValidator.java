/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class IdFormatValidator implements ConstraintValidator<IdFormat, String> {

    @Value("${mosip.esignet.supported-id-regex}")
    private String supportedRegex;

    private static final String DEFAULT_SUPPORTED_REGEX = "\\S*";

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if(s == null || s.isBlank())
            return false;

        return (supportedRegex == null) ?
                s.matches(DEFAULT_SUPPORTED_REGEX) : s.matches(supportedRegex);
    }
}
