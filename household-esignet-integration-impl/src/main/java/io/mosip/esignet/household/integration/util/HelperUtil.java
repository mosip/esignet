package io.mosip.esignet.household.integration.util;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.household.integration.exception.HouseholdentityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static io.mosip.esignet.household.integration.util.ErrorConstants.INVALID_ALGORITHM;
import static io.mosip.esignet.household.integration.util.ErrorConstants.INVALID_AUTHENTICATION;

@Slf4j
public class HelperUtil {
    public static final String ALGO_SHA3_256 = "SHA3-256";
    private static Base64.Encoder urlSafeEncoder;
    private static Base64.Decoder urlSafeDecoder;
    private static PathMatcher pathMatcher;

    static {
        urlSafeEncoder = Base64.getUrlEncoder().withoutPadding();
        urlSafeDecoder = Base64.getUrlDecoder();
        pathMatcher = new AntPathMatcher();
    }

    public static String generateB64EncodedHash(String algorithm, String value) throws KycAuthException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return urlSafeEncoder.encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Invalid algorithm : {}", algorithm, ex);
            throw new KycAuthException(INVALID_ALGORITHM);
        }
    }

    public static String b64Encode(String value) {
        return urlSafeEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

}
