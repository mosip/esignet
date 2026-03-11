/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class OIDCPromptValidator implements ConstraintValidator<OIDCPrompt, String> {

    @Value("#{${mosip.esignet.supported.ui.prompts}}")
    private List<String> supportedPrompts;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty())
            return true; // As this is OPTIONAL parameter

        // Split by space and trim each token
        String[] promptTokens = value.trim().split("\\s+");

        // reject if any token is none/login
        for (String token : promptTokens) {
            if (Constants.NONE.equals(token) || Constants.LOGIN.equals(token)) {
                return false; // → login_required
            }
        }

        return true;
    }
}
