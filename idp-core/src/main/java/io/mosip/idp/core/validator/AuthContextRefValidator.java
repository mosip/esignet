/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.validator;

import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class AuthContextRefValidator implements ConstraintValidator<AuthContextRef, String> {

    @Autowired
    AuthenticationContextClassRefUtil acrUtil;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null)
            return true; // As this is OPTIONAL parameter

        String[] values = IdentityProviderUtil.splitAndTrimValue(value, Constants.SPACE);
        try {
            return acrUtil.getSupportedACRValues().containsAll(List.of(values));
        } catch (IdPException e) {}
        return false;
    }
}
