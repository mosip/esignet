/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import org.apache.commons.codec.binary.Hex;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import com.nimbusds.jose.util.ByteUtils;

import lombok.extern.slf4j.Slf4j;

import javax.xml.bind.DatatypeConverter;

@Slf4j
public class IdentityProviderUtil {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderUtil.class);
    public static final String ALGO_SHA3_256 = "SHA3-256";
    public static final String ALGO_SHA_256 = "SHA-256";
    public static final String ALGO_SHA_1 = "SHA-1";
    public static final String ALGO_MD5 = "MD5";
    public static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static Base64.Encoder urlSafeEncoder;
    private static Base64.Decoder urlSafeDecoder;
    private static PathMatcher pathMatcher;

    static {
        urlSafeEncoder = Base64.getUrlEncoder().withoutPadding();
        urlSafeDecoder = Base64.getUrlDecoder();
        pathMatcher = new AntPathMatcher();
    }

    /**
     * Output format : 2022-12-01T03:22:46.720Z
     * @return Formatted datetime
     */
    public static String getUTCDateTime() {
        return ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN));
    }

    /**
     * Output format : 2022-12-01T03:22:46.722904874
     * @return datetime
     */
    public static String getUTCDateTimeWithNanoSeconds() {
        return ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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
            //taking only 16 bytes (=128 bits)
            byte[] leftMost128Bits = ByteUtils.subArray(hash, 0, 16);
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
        throw new InvalidRequestException(ErrorConstants.INVALID_REDIRECT_URI);
    }

    public static String createTransactionId(String nonce) throws IdPException {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGO_SHA3_256);
            digest.update(UUID.randomUUID().toString()
                    .concat(nonce == null ? getUTCDateTimeWithNanoSeconds() : nonce)
                    .concat(generateRandomAlphaNumeric(10))
                    .getBytes(StandardCharsets.UTF_8));
            return urlSafeEncoder.encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            log.error("create transaction id failed with alg SHA3-256", ex);
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
    }

    public static String generateRandomAlphaNumeric(int length) {
        StringBuilder builder = new StringBuilder();
        for(int i=0; i<length; i++) {
            int index = ThreadLocalRandom.current().nextInt(CHARACTERS.length());
            builder.append(CHARACTERS.charAt(index));
        }
        return builder.toString();
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

	public static String getJWKString(Map<String, Object> jwk) throws IdPException {
		try {
			RsaJsonWebKey jsonWebKey = new RsaJsonWebKey(jwk);
			return jsonWebKey.toJson();
		} catch (JoseException e) {
			log.error(ErrorConstants.INVALID_PUBLIC_KEY, e);
			throw new IdPException(ErrorConstants.INVALID_PUBLIC_KEY);
		}
	}

    public static String getCertificateThumbprint(String algorithm, X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(cert.getEncoded());
            return DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
    }
}
