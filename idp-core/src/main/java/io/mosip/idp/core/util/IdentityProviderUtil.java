/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.util;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;

public class IdentityProviderUtil {

    public static String getResponseTime() {
        return ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));
    }

    public static String[] splitAndTrimValue(String value, String separator) {
        if(value == null)
            return new String[]{};

        return Arrays.stream(value.split(separator))
                .map(String::trim)
                .toArray(String[]::new);
    }
}
