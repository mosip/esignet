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

import static io.mosip.esignet.household.integration.util.ErrorConstants.INVALID_AUTHENTICATION;

@Slf4j
public class HelperUtil {
    public static final String ALGO_SHA3_256 = "SHA3-256";
    public static final String ALGO_SHA_256 = "SHA-256";
    public static final String ALGO_SHA_1 = "SHA-1";
    public static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
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
     *
     * @return Formatted datetime
     */
    public static String getCurrentUTCDateTime() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));
    }

    public static String generateB64EncodedHash(String algorithm, String value) throws KycAuthException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return urlSafeEncoder.encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Invalid algorithm : {}", algorithm, ex);
            throw new KycAuthException(INVALID_AUTHENTICATION);
        }
    }

    public static String b64Encode(byte[] bytes) {
        return urlSafeEncoder.encodeToString(bytes);
    }

    public static String b64Encode(String value) {
        return urlSafeEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public  static String generateJwsToken(String kycToken) throws KycExchangeException {
        // Generate an RSA key pair
        try{
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Create a JWT claims set
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(kycToken)
                    .build();

            // Create a JWS header with the RSA signature algorithm
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID("key-id")
                    .build();

            // Create a JWS object and sign it with the private key
            JWSObject jwsObject = new JWSObject(header, new Payload(claimsSet.toJSONObject()));
            JWSSigner signer = new RSASSASigner((RSAPrivateKey) keyPair.getPrivate());
            jwsObject.sign(signer);
            String token = jwsObject.serialize();
            return token;
        }catch (NoSuchAlgorithmException ex) {
            throw new KycExchangeException("Error while generating JWS token");
        } catch (JOSEException ex) {
            throw new KycExchangeException("Error while generating JWS token");
        }


        // Serialize the JWS object to a compact string


        // Verify the JWS signature using the public key
//        JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
//        RSAPublicKey rsaPublicKey= (RSAPublicKey) keyPair.getPublic();
//        RSAPrivateKey rsaPrivateKey= (RSAPrivateKey) keyPair.getPrivate();
//        String privatekey = Base64.getEncoder().encodeToString(rsaPrivateKey.getEncoded());
//        String publicKey = Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded());
//
//        boolean isValid = jwsObject.verify(verifier);
//
//        System.out.println("JWS token: " + token);
//        System.out.println("Is JWS signature valid? " + isValid+ " public key: "+publicKey);
//        System.out.println("Is JWS signature valid? " + isValid+ " private key: "+privatekey);
    }

}
