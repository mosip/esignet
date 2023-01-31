/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.constants.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RequestTimeValidator implements ConstraintValidator<RequestTime, String> {

    @Value("${mosip.esignet.reqtime.leeway-minutes:2}")
    private int leewayInMinutes;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        try {
            LocalDateTime providedDt = LocalDateTime.parse(value, DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN));
            LocalDateTime futureDt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(leewayInMinutes);
            LocalDateTime oldDt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(leewayInMinutes);
            return (providedDt.isAfter(oldDt) && providedDt.isBefore(futureDt));
        } catch (Exception ex) {}

        return false;
    }
}
