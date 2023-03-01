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
import java.util.Map;


public class OIDCClaimValidator implements ConstraintValidator<OIDCClaim, String> {

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        List<String> supportedClaims = (List<String>) discoveryMap.get("claims_supported");
        return supportedClaims.contains(value);
    }
}
