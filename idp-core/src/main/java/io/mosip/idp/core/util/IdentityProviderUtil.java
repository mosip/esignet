/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.util;

import com.nimbusds.jose.util.ByteUtils;
import io.mosip.idp.core.exception.IdPException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;

@Slf4j
public class IdentityProviderUtil {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderUtil.class);
    public static final String ALGO_SHA3_256 = "SHA3-256";
    public static final String ALGO_SHA_256 = "SHA-256";
    public static final String ALGO_MD5 = "MD5";
 // lookup array for converting byte to hex
 	private static final char[] LOOKUP_TABLE_LOWER = new char[] { 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38,
 			0x39, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66 };
 	private static final char[] LOOKUP_TABLE_UPPER = new char[] { 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38,
 			0x39, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46 };

    private static Base64.Encoder urlSafeEncoder;
    private static Base64.Decoder urlSafeDecoder;
    private static PathMatcher pathMatcher;

    static {
        urlSafeEncoder = Base64.getUrlEncoder().withoutPadding();
        urlSafeDecoder = Base64.getUrlDecoder();
        pathMatcher = new AntPathMatcher();
    }

    public static String getUTCDateTime() {
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

    public static String generateHexEncodedHash(String algorithm, String value) throws IdPException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hash);
        } catch (NoSuchAlgorithmException ex) {
            logger.error("Invalid algorithm : {}", algorithm, ex);
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
    }

    public static String generateB64EncodedHash(String algorithm, String value) throws IdPException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return urlSafeEncoder.encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            logger.error("Invalid algorithm : {}", algorithm, ex);
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
    }

    public static String b64Encode(byte[] bytes) {
        return urlSafeEncoder.encodeToString(bytes);
    }

    public static String b64Encode(String value) {
        return urlSafeEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] b64Decode(String value) {
        return urlSafeDecoder.decode(value);
    }

    /**
     *  if the alg is RS256, hash the access_token value with SHA-256, then take the left-most 128 bits and base64url
     *  encode them. The at_hash value is a case-sensitive string.
     * @param accessToken
     * @return
     * @throws IdPException
     */
    public static String generateOIDCAtHash(String accessToken) throws IdPException {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGO_SHA_256);
            byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.UTF_8));
            byte[] leftMost128Bits = ByteUtils.subArray(hash, 0, 32);
            return urlSafeEncoder.encodeToString(leftMost128Bits);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Access token hashing failed with alg:{}", ALGO_SHA_256, ex);
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
    }

    public static long getEpochSeconds() {
        return ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
    }

    public static void validateRedirectURI(List<String> registeredRedirectUris, String requestedRedirectUri) throws IdPException {
        if(registeredRedirectUris.stream().anyMatch(uri -> matchUri(uri, requestedRedirectUri)))
            return;

        log.error("Invalid redirect URI registered : {}, requested: {}", registeredRedirectUris, requestedRedirectUri);
        throw new IdPException(ErrorConstants.INVALID_REDIRECT_URI);
    }

    //TODO - Get this verified by sasi & taheer
    public static String createTransactionId(String nonce) throws IdPException {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGO_SHA3_256);
            digest.update(UUID.randomUUID().toString()
                    .concat(nonce == null ? getUTCDateTime() : nonce)
                    .getBytes(StandardCharsets.UTF_8));
            return urlSafeEncoder.encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            log.error("create transaction id failed with alg SHA3-256", ex);
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
    }

    private static boolean matchUri(String registeredUri, String requestedUri) {
        try {
            URL registered = new URL(registeredUri);
            URL requested = new URL(requestedUri);
            return registered.getProtocol().equalsIgnoreCase(requested.getProtocol()) &&
                    registered.getHost().equalsIgnoreCase(requested.getHost()) &&
                    pathMatcher.match(registered.getFile(), requested.getFile());
        } catch (MalformedURLException e) {
            log.error("Invalid redirect URLs found during validation", e);
        }
        return false;
    }
    
    public static byte[] generateSalt(int bytes) {
		SecureRandom random = new SecureRandom();
		byte[] randomBytes = new byte[bytes];
		random.nextBytes(randomBytes);
		return randomBytes;
	}
	
	public static String digestAsPlainTextWithSalt(final byte[] password, final byte[] salt) throws IdPException
			 {
		MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance(ALGO_SHA_256);
			messageDigest.update(password);
			messageDigest.update(salt);
		} catch (NoSuchAlgorithmException e) {
			throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
		}
		
		return encodeBytesToHex(messageDigest.digest(), true, ByteOrder.BIG_ENDIAN);
	}
	public static String encodeBytesToHex(byte[] byteArray, boolean upperCase, ByteOrder byteOrder) {

		// our output size will be exactly 2x byte-array length
		final char[] buffer = new char[byteArray.length * 2];

		// choose lower or uppercase lookup table
		final char[] lookup = upperCase ? LOOKUP_TABLE_UPPER : LOOKUP_TABLE_LOWER;

		int index;
		for (int i = 0; i < byteArray.length; i++) {
			// for little endian we count from last to first
			index = (byteOrder == ByteOrder.BIG_ENDIAN) ? i : byteArray.length - i - 1;

			// extract the upper 4 bit and look up char (0-A)
			buffer[i << 1] = lookup[(byteArray[index] >> 4) & 0xF];
			// extract the lower 4 bit and look up char (0-A)
			buffer[(i << 1) + 1] = lookup[(byteArray[index] & 0xF)];
		}
		return new String(buffer);
	}
}
