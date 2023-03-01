/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.validator;

import org.springframework.beans.factory.annotation.Value;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

public class OIDCClientAuthValidator implements ConstraintValidator<OIDCClientAuth, String> {

    @Value("#{${mosip.esignet.supported.client.auth.methods}}")
    private List<String> supportedClientAuthMethods;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        return supportedClientAuthMethods.contains(value);
    }
}
