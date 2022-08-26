/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.util;

import io.mosip.idp.core.exception.IdPException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;

public class IdentityProviderUtil {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderUtil.class);

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

    public static String generateHash(String algorithm, String value) throws IdPException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hash);
        } catch (NoSuchAlgorithmException ex) {
            logger.error("Invalid algorithm : {}", algorithm, ex);
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
    }

    public static long getEpochSeconds() {
        return ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
    }
}
