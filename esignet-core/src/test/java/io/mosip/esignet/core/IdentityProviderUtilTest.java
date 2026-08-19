/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.validator.RedirectURLValidator;

public class IdentityProviderUtilTest {

    private IdentityProviderUtil identityProviderUtil;

    @BeforeEach
    public void setUp() {
        RedirectURLValidator validator = new RedirectURLValidator(new String[]{"corp", "internal"});
        Map<String, List<String>> hashFields = Map.of(
                "RSA", List.of("n"),
                "EC", List.of("x", "y")
        );
        identityProviderUtil = new IdentityProviderUtil(validator, hashFields);
    }

    @Test
    public void validateRedirectURIPositiveTest() throws EsignetException {
        identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/**"),
                "https://api.dev.mosip.net/home/test");
        identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test"),
                "https://api.dev.mosip.net/home/test");
        identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test?"),
                "https://api.dev.mosip.net/home/test1");
        identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/*"),
                "https://api.dev.mosip.net/home/werrrwqfdsfg5fgs34sdffggdfgsdfg?state=reefdf");
        identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                "https://api.dev.mosip.net/home/testament?rr=rrr");
        identityProviderUtil.validateRedirectURI(Arrays.asList("io.mosip.residentapp://oauth"),
                "io.mosip.residentapp://oauth");
        identityProviderUtil.validateRedirectURI(Arrays.asList("https://sso.idp.corp/callback"),
                "https://sso.idp.corp/callback");
        identityProviderUtil.validateRedirectURI(Arrays.asList("https://portal.company.internal/**"),
                "https://portal.company.internal/auth/callback");
    }

    @Test
    public void validateRedirectURINegativeTest() {
        try {
            identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/test");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home**"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            identityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            identityProviderUtil.validateRedirectURI(Arrays.asList("test-url"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}
        try {
            identityProviderUtil.validateRedirectURI(Arrays.asList("HTTPS://DEV.MOSIP.NET/home"),
                    "https://dev.mosip.net/home");
            Assertions.fail();
        } catch (EsignetException e) {}
    }

    @Test
    public void test_dateTime() {
        Assertions.assertNotNull(IdentityProviderUtil.getUTCDateTime());
        Assertions.assertNotNull(IdentityProviderUtil.getUTCDateTimeWithNanoSeconds());
        Assertions.assertTrue(IdentityProviderUtil.getEpochSeconds() > 0);
    }

    @Test
    public void test_splitAndTrimValue() {
        Assertions.assertTrue(IdentityProviderUtil.splitAndTrimValue("test split", " ").length == 2);
        Assertions.assertTrue(IdentityProviderUtil.splitAndTrimValue(null, " ").length == 0);
    }

    @Test
    public void test_generateHexEncodedHash() {
        Assertions.assertNotNull(IdentityProviderUtil.generateHexEncodedHash("sha-256", "test-hexencoded-hash"));
        try {
            IdentityProviderUtil.generateHexEncodedHash("test-algorithm", "test");
            Assertions.fail();
        } catch (EsignetException e) {}
    }

    @Test
    public void test_generateB64EncodedHash() {
        Assertions.assertNotNull(IdentityProviderUtil.generateB64EncodedHash("sha-256", "test-b64-hash"));
        try {
            IdentityProviderUtil.generateB64EncodedHash("test-algorithm", "test");
            Assertions.fail();
        } catch (EsignetException e) {}
    }

    @Test
    public void test_encodeDecode() {
        Assertions.assertNotNull(IdentityProviderUtil.b64Encode("test-encode-string"));
        Assertions.assertNotNull(IdentityProviderUtil.b64Encode("test-bytes".getBytes()));
        Assertions.assertNotNull(IdentityProviderUtil.b64Decode("test-decode-string"));
    }

    @Test
    public void test_generateOIDCAtHash() {
        Assertions.assertNotNull(IdentityProviderUtil.generateOIDCAtHash("test-access-token"));
    }

    @Test
    public void test_createTransactionId() {
        Assertions.assertNotNull(IdentityProviderUtil.createTransactionId(null));
        Assertions.assertNotNull(IdentityProviderUtil.createTransactionId(IdentityProviderUtil.getUTCDateTimeWithNanoSeconds()));
    }

    @Test
    public void test_generateSalt() {
        Assertions.assertNotNull(IdentityProviderUtil.generateSalt(2048));
    }

    @Test
    public void getJWKString_withValidAndMissingKty_thenFail() {
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("alg", "RS256");
        jwk.put("use", "sig");
        EsignetException ex = Assertions.assertThrows(EsignetException.class,
                () -> IdentityProviderUtil.getJWKString(jwk));
        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, ex.getMessage());
    }

    @Test
    public void getJWKString_withEmptyAlgOrUse_thenFail() {
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("n", "oahUIzUup5kqncCkHk5Zb1pRrLx7e6YtM-9jX1f5e6mHnZFkC2LJUZ0sEh0n5Y5KnQfW9s7d7gK2b8P0EEl0h3ZyHkWzA3YbsgzB4pDxP4RxMZ1I8xD2z3UvfA1zjvKDHz6wEweq4hVJ8nS8GzZJ2E_vb3s");
        jwk.put("e", "AQAB");
        jwk.put("alg", "");
        jwk.put("use", "");
        EsignetException ex = Assertions.assertThrows(EsignetException.class,
                () -> IdentityProviderUtil.getJWKString(jwk));
        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, ex.getMessage());
    }

    @Test
    public void getJWKString_withMissingAlg_thenFail() {
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        EsignetException ex = Assertions.assertThrows(EsignetException.class,
                () -> IdentityProviderUtil.getJWKString(jwk));
        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, ex.getMessage());
    }

    @Test
    public void getJWKString_withMissingUse_thenFail() {
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("alg", "RS256");
        EsignetException ex = Assertions.assertThrows(EsignetException.class,
                () -> IdentityProviderUtil.getJWKString(jwk));
        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, ex.getMessage());
    }

    @Test
    public void getJWKString_withUnsupportedKty_thenFail() {
        Map<String, Object> jwkMap = new HashMap<>();
        jwkMap.put("kty", "OCT");
        try {
            IdentityProviderUtil.getJWKString(jwkMap);
            Assertions.fail("Expected EsignetException was not thrown");
        } catch (EsignetException e) {
            Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, e.getMessage());
        }
    }

    @Test
    public void getJWKString_withValidRSAKey_thenPass() throws Exception {
        Map<String, Object> jwkMap = new HashMap<>();
        jwkMap.put("kty", "RSA");
        jwkMap.put("n", "oahUIzUup5kqncCkHk5Zb1pRrLx7e6YtM-9jX1f5e6mHnZFkC2LJUZ0sEh0n5Y5KnQfW9s7d7gK2b8P0EEl0h3ZyHkWzA3YbsgzB4pDxP4RxMZ1I8xD2z3UvfA1zjvKDHz6wEweq4hVJ8nS8GzZJ2E_vb3s");
        jwkMap.put("e", "AQAB");
        jwkMap.put("alg", "RS256");
        jwkMap.put("use", "sig");
        String jwkJson = IdentityProviderUtil.getJWKString(jwkMap);
        Assertions.assertTrue(jwkJson.contains("\"kty\":\"RSA\""));
    }

    @Test
    public void getJWKString_withValidFullRSAKey_thenPass() {
        Map<String, Object> jwkMap = new HashMap<>();
        jwkMap.put("kty", "RSA");
        jwkMap.put("n", "oahUIzUup5kqncCkHk5Zb1pRrLx7e6YtM-9jX1f5e6mHnZFkC2LJUZ0sEh0n5Y5KnQfW9s7d7gK2b8P0EEl0h3ZyHkWzA3YbsgzB4pDxP4RxMZ1I8xD2z3UvfA1zjvKDHz6wEweq4hVJ8nS8GzZJ2E_vb3s");
        jwkMap.put("e", "AQAB");
        jwkMap.put("alg", "RS256");
        jwkMap.put("use", "sig");
        String jwkJson = IdentityProviderUtil.getJWKString(jwkMap);
        Assertions.assertTrue(jwkJson.contains("\"kty\":\"RSA\""));
    }

    @Test
    public void getJWKString_withInvalidAlgForRSA_thenFail() {
        Map<String, Object> jwkMap = new HashMap<>();
        jwkMap.put("kty", "RSA");
        jwkMap.put("n", "oahUIzUup5kqncCkHk5Zb1pRrLx7e6YtM-9jX1f5e6mHnZFkC2LJUZ0sEh0n5Y5KnQfW9s7d7gK2b8P0EEl0h3ZyHkWzA3YbsgzB4pDxP4RxMZ1I8xD2z3UvfA1zjvKDHz6wEweq4hVJ8nS8GzZJ2E_vb3s");
        jwkMap.put("e", "AQAB");
        jwkMap.put("alg", "");

        EsignetException ex = Assertions.assertThrows(EsignetException.class,
                () -> IdentityProviderUtil.getJWKString(jwkMap));
        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, ex.getMessage());
    }

    @Test
    public void getJWKString_withInvalidUse_thenFail() {
        Map<String, Object> jwkMap = new HashMap<>();
        jwkMap.put("kty", "RSA");
        jwkMap.put("n", "oahUIzUup5kqncCkHk5Zb1pRrLx7e6YtM-9jX1f5e6mHnZFkC2LJUZ0sEh0n5Y5KnQfW9s7d7gK2b8P0EEl0h3ZyHkWzA3YbsgzB4pDxP4RxMZ1I8xD2z3UvfA1zjvKDHz6wEweq4hVJ8nS8GzZJ2E_vb3s");
        jwkMap.put("e", "AQAB");
        jwkMap.put("use", " ");

        EsignetException ex = Assertions.assertThrows(EsignetException.class,
                () -> IdentityProviderUtil.getJWKString(jwkMap));
        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, ex.getMessage());
    }

    @Test
    public void getJWKString_withValidEncryptionKey_thenPass() {
        Map<String, Object> jwkMap = new HashMap<>();
        jwkMap.put("kty", "RSA");
        jwkMap.put("n", "oahUIzUup5kqncCkHk5Zb1pRrLx7e6YtM-9jX1f5e6mHnZFkC2LJUZ0sEh0n5Y5KnQfW9s7d7gK2b8P0EEl0h3ZyHkWzA3YbsgzB4pDxP4RxMZ1I8xD2z3UvfA1zjvKDHz6wEweq4hVJ8nS8GzZJ2E_vb3s");
        jwkMap.put("e", "AQAB");
        jwkMap.put("alg", "RSA-OAEP-256");
        jwkMap.put("use", "enc");

        String jwkJson = IdentityProviderUtil.getJWKString(jwkMap);
        Assertions.assertTrue(jwkJson.contains("\"kty\":\"RSA\""));
        Assertions.assertTrue(jwkJson.contains("\"use\":\"enc\""));
        Assertions.assertTrue(jwkJson.contains("\"alg\":\"RSA-OAEP-256\""));
    }

    @Test
    public void getJWKString_withValidECKey_thenPass() {
        Map<String, Object> jwkMap = new HashMap<>();
        jwkMap.put("kty", "EC");
        jwkMap.put("crv", "P-256");
        jwkMap.put("x", "f83OJ3D2xF4vOQ6aE1n8bQJ8iTo2DJH6TLO8kMZb3mg");
        jwkMap.put("y", "x_FEzRu9l1tlZRjGZkIvYyC6i76h3C1j6w9kq3fJSNc");
        jwkMap.put("alg", "ES256");
        jwkMap.put("use", "sig");
        String jwkJson = IdentityProviderUtil.getJWKString(jwkMap);
        Assertions.assertTrue(jwkJson.contains("\"kty\":\"EC\""));
    }

    @Test
    public void test_getCertificateThumbprint() throws Exception {
        Assertions.assertNotNull(IdentityProviderUtil.getCertificateThumbprint("SHA-256", getCertificate()));
        try {
            IdentityProviderUtil.getCertificateThumbprint("test", getCertificate());
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(e.getMessage(), ErrorConstants.INVALID_ALGORITHM);
        }
    }

    @Test
    public void test_generateThumbprintByCertificate() throws EsignetException {
        String thumbprint = "YfRxd-cG6urE1r_Ij7yRwMzt0JHoIadZ-lqkdlE0FYo";
        String certificateString = """
                -----BEGIN CERTIFICATE-----
                MIICrzCCAZegAwIBAgIGAYohPDZlMA0GCSqGSIb3DQEBCwUAMBMxETAPBgNVBAMT
                CE1vY2stSURBMB4XDTIzMDgyMzAxNDE0OFoXDTIzMDkwMjAxNDE0OFowHjEcMBoG
                A1UEAxMTU2lkZGhhcnRoIEsgTWFuc291cjCCASIwDQYJKoZIhvcNAQEBBQADggEP
                ADCCAQoCggEBANcfMOxGBmCZ0sn/Fr1ZvGE1nl0zOxTdhSPkLxgHpq09minv6HsJ
                Om9Y5FBbPQavSYdliFO/61VlOMnKYpCKXx+Rf/+QCBgx4/Wc57bu3xmNtxl76ARh
                HnRGWEz0UH/JX2mX1XgnHSBMgS8F+ckQuvoA7vN/LTIxXl89OkUyHa7HIylvQpsS
                8bv7qXohaHf6IjbQGbjdSpKlLhNgOtgPWHxQu6nzBqtTR/Ks1S1zutfv8p5gip4F
                vLGQ68Il+Nco6vcvKmYIqBZQyMwMBGxYzwmDFeLMBjMi5LR3Qikj/BaH2aVPX8Zg
                D2TqeUvYzobV8Xc+qV6XnGkQdRNKDBKYGmcCAwEAATANBgkqhkiG9w0BAQsFAAOC
                AQEAo7Tjx59tq1hSv6XaGw2BUnBKPqyGpmHDb9y6VXQXkI2YAZghtDoebeppCnrU
                d5219dwEgM0FoUW3pumMN/rM5NGXljktMp5xhyYU1rbBwvj8mGg9YTv7oUk1IQ0K
                keecYS5ZFmbz0N5CgbitJginXn4HKTPd9CEXYEBtkO7C7Onl0LbnH0g2grVuNGqH
                pD5P6TbGJzwrlnxstOCyCVMmRfVIpQFTygMpNjDQTlsXwWt4ZEf/ZiB2W4zYcDMk
                cXGZv5rZBqX/uuptptN7HhYD45Ir4ZAyNFlZuPusQvxiSm674bCkV3lN6oH0Jw2/
                dHnX5TRuFoits1+jx3cNSBHmjA==
                -----END CERTIFICATE-----
                """;
        Assertions.assertEquals(thumbprint, IdentityProviderUtil.generateCertificateThumbprint(certificateString));
        try {
            IdentityProviderUtil.generateCertificateThumbprint("test");
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(e.getMessage(), ErrorConstants.INVALID_CERTIFICATE);
        }
    }

    public static JWK generateJWK_RSA() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private X509Certificate getCertificate() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        X500Name issuer = new X500Name("CN=Test");
        BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, notBefore, notAfter, issuer, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }
}
