/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.validators;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static io.mosip.idp.util.Constants.UTC_DATETIME_PATTERN;

@Component
public class RequestTimeValidator implements ConstraintValidator<RequestTime, String> {

    @Value("${mosip.idp.reqtime.maxlimit:-5}")
    private int maxMinutes;

    @Value("${mosip.idp.reqtime.minlimit:5}")
    private int minMinutes;


    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null || value.isBlank())
            return false;

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(value, DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));
            long diff = localDateTime.until(LocalDateTime.now(ZoneOffset.UTC), ChronoUnit.MINUTES);
            return (diff <= minMinutes && diff >= maxMinutes);
        } catch (Exception ex) {}

        return false;
    }
}
