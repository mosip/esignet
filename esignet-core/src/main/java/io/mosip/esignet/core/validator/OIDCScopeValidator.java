/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;

@Component
public class OIDCScopeValidator implements ConstraintValidator<OIDCScope, String>  {

    @Value("#{${mosip.esignet.supported.authorize.scopes}}")
    private List<String> authorizeScopes;

    @Value("#{${mosip.esignet.supported.openid.scopes}}")
    private List<String> openidScopes;

    @Value("#{${mosip.esignet.supported.credential.scopes}}")
    private List<String> credentialScopes;

    /**
     * 1. Unknown scopes are ignored
     * 2. Provided scope should have at least one of authorize / openid scope
     * 3. If found any openid scopes then 'openid' MUST also be present in the scopes
     *
     * @param value object to validate
     * @param context context in which the constraint is evaluated
     *
     * @return
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        String[] scopes = IdentityProviderUtil.splitAndTrimValue(value, Constants.SPACE);
        boolean openid = Arrays.stream(scopes).anyMatch( s -> Constants.SCOPE_OPENID.equals(s));
        String[] authorized_scopes = Arrays.stream(scopes)
                .filter( s -> authorizeScopes.contains(s))
                .toArray(String[]::new);
        String[] openid_scopes = Arrays.stream(scopes)
                .filter( s -> openidScopes.contains(s) || Constants.SCOPE_OPENID.equals(s))
                .toArray(String[]::new);
        String[] credential_scopes = Arrays.stream(scopes)
                .filter( s -> credentialScopes.contains(s))
                .toArray(String[]::new);

        //at least one of authorize / openid / credential scope MUST be present
        if(!openid && authorized_scopes.length == 0 && openid_scopes.length == 0 && credential_scopes.length == 0)
            return false;

        //With credential scope, no other type of scopes are allowed
        if(credential_scopes.length > 0 && (openid || authorized_scopes.length > 0 || openid_scopes.length > 0))
            return false;

        //any openid scopes then 'openid' MUST also be present
        if(openid_scopes.length > 0 && !openid)
            return false;

        return true;
    }
}
