/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class OIDCScopeValidator implements ConstraintValidator<OIDCScope, String>  {

    @Value("#{${mosip.idp.supported.scopes}}")
    private List<String> supportedScopes;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        //TODO - space separated string
        //TODO - unknown scope should be ignored
        //TODO - provided scope should have atleast one of authorize / openid scope
        //TODO - if found any openid scopes then 'openid' must also be present in the scopes
        return supportedScopes.contains(value);
    }
}
