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
import java.util.Map;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import io.mosip.esignet.core.constants.ErrorConstants;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.Assert;
import org.junit.Test;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.IdentityProviderUtil;

public class IdentityProviderUtilTest {


    @Test
    public void validateRedirectURIPositiveTest() throws EsignetException {
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/**"),
                "https://api.dev.mosip.net/home/test");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test"),
                "https://api.dev.mosip.net/home/test");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test?"),
                "https://api.dev.mosip.net/home/test1");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/*"),
                "https://api.dev.mosip.net/home/werrrwqfdsfg5fgs34sdffggdfgsdfg?state=reefdf");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                "https://api.dev.mosip.net/home/testament?rr=rrr");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("io.mosip.residentapp://oauth"),
                "io.mosip.residentapp://oauth");
    }

    @Test
    public void validateRedirectURINegativeTest() {
        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/test");
            Assert.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home**"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (EsignetException e) {}
        
        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("test-url"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (EsignetException e) {}
        try {
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("HTTPS://DEV.MOSIP.NET/home"),
                "https://dev.mosip.net/home");
            Assert.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_dateTime() {
    	Assert.assertNotNull(IdentityProviderUtil.getUTCDateTime());
    	Assert.assertNotNull(IdentityProviderUtil.getUTCDateTimeWithNanoSeconds());
    	Assert.assertTrue(IdentityProviderUtil.getEpochSeconds() > 0);
    }
    
    @Test
    public void test_splitAndTrimValue() {
    	Assert.assertTrue(IdentityProviderUtil.splitAndTrimValue("test split", " ").length == 2);
    	Assert.assertTrue(IdentityProviderUtil.splitAndTrimValue(null, " ").length == 0);
    }
    
    @Test
    public void test_generateHexEncodedHash() {
    	Assert.assertNotNull(IdentityProviderUtil.generateHexEncodedHash("sha-256", "test-hexencoded-hash"));
    	try {
    		IdentityProviderUtil.generateHexEncodedHash("test-algorithm", "test");
            Assert.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_generateB64EncodedHash() {
    	Assert.assertNotNull(IdentityProviderUtil.generateB64EncodedHash("sha-256", "test-b64-hash"));
    	try {
    		IdentityProviderUtil.generateB64EncodedHash("test-algorithm", "test");
            Assert.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_encodeDecode() {
    	Assert.assertNotNull(IdentityProviderUtil.b64Encode("test-encode-string"));
    	Assert.assertNotNull(IdentityProviderUtil.b64Encode("test-bytes".getBytes()));
    	Assert.assertNotNull(IdentityProviderUtil.b64Decode("test-decode-string"));
    }
    
    @Test
    public void test_generateOIDCAtHash() {
    	Assert.assertNotNull(IdentityProviderUtil.generateOIDCAtHash("test-access-token"));
    }
    
    @Test
    public void test_createTransactionId() {
    	Assert.assertNotNull(IdentityProviderUtil.createTransactionId(null));
    	Assert.assertNotNull(IdentityProviderUtil.createTransactionId(IdentityProviderUtil.getUTCDateTimeWithNanoSeconds()));
    }
    
    @Test
    public void test_generateSalt() {
    	Assert.assertNotNull(IdentityProviderUtil.generateSalt(2048));
    }
    
    @Test
    public void test_getJWKString() {
    	Assert.assertNotNull(IdentityProviderUtil.getJWKString((Map<String, Object>) generateJWK_RSA().getRequiredParams()));
    	try {
    		IdentityProviderUtil.getJWKString(new HashMap<String, Object>());
    		Assert.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_getCertificateThumbprint() throws Exception {
    	Assert.assertNotNull(IdentityProviderUtil.getCertificateThumbprint("SHA-256", getCertificate()));
    	try {
    		IdentityProviderUtil.getCertificateThumbprint("test", getCertificate());
    		Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(e.getMessage(),ErrorConstants.INVALID_ALGORITHM);
        }
    }

    @Test
    public void test_generateThumbprintByCertificate()throws EsignetException{
        String thumbprint="YfRxd-cG6urE1r_Ij7yRwMzt0JHoIadZ-lqkdlE0FYo";
        String certificateString="-----BEGIN CERTIFICATE-----\n" +
                "MIICrzCCAZegAwIBAgIGAYohPDZlMA0GCSqGSIb3DQEBCwUAMBMxETAPBgNVBAMT\n" +
                "CE1vY2stSURBMB4XDTIzMDgyMzAxNDE0OFoXDTIzMDkwMjAxNDE0OFowHjEcMBoG\n" +
                "A1UEAxMTU2lkZGhhcnRoIEsgTWFuc291cjCCASIwDQYJKoZIhvcNAQEBBQADggEP\n" +
                "ADCCAQoCggEBANcfMOxGBmCZ0sn/Fr1ZvGE1nl0zOxTdhSPkLxgHpq09minv6HsJ\n" +
                "Om9Y5FBbPQavSYdliFO/61VlOMnKYpCKXx+Rf/+QCBgx4/Wc57bu3xmNtxl76ARh\n" +
                "HnRGWEz0UH/JX2mX1XgnHSBMgS8F+ckQuvoA7vN/LTIxXl89OkUyHa7HIylvQpsS\n" +
                "8bv7qXohaHf6IjbQGbjdSpKlLhNgOtgPWHxQu6nzBqtTR/Ks1S1zutfv8p5gip4F\n" +
                "vLGQ68Il+Nco6vcvKmYIqBZQyMwMBGxYzwmDFeLMBjMi5LR3Qikj/BaH2aVPX8Zg\n" +
                "D2TqeUvYzobV8Xc+qV6XnGkQdRNKDBKYGmcCAwEAATANBgkqhkiG9w0BAQsFAAOC\n" +
                "AQEAo7Tjx59tq1hSv6XaGw2BUnBKPqyGpmHDb9y6VXQXkI2YAZghtDoebeppCnrU\n" +
                "d5219dwEgM0FoUW3pumMN/rM5NGXljktMp5xhyYU1rbBwvj8mGg9YTv7oUk1IQ0K\n" +
                "keecYS5ZFmbz0N5CgbitJginXn4HKTPd9CEXYEBtkO7C7Onl0LbnH0g2grVuNGqH\n" +
                "pD5P6TbGJzwrlnxstOCyCVMmRfVIpQFTygMpNjDQTlsXwWt4ZEf/ZiB2W4zYcDMk\n" +
                "cXGZv5rZBqX/uuptptN7HhYD45Ir4ZAyNFlZuPusQvxiSm674bCkV3lN6oH0Jw2/\n" +
                "dHnX5TRuFoits1+jx3cNSBHmjA==\n" +
                "-----END CERTIFICATE-----\n";
        Assert.assertEquals(thumbprint,IdentityProviderUtil.generateCertificateThumbprint(certificateString));
        try {
            IdentityProviderUtil.generateCertificateThumbprint("test");
            Assert.fail();
        } catch (EsignetException e) {
            Assert.assertEquals(e.getMessage(),ErrorConstants.INVALID_CERTIFICATE);
        }
    }
    
    public static JWK generateJWK_RSA() {
        // Generate the RSA key pair
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            // Convert public key to JWK format
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
    
    private X509Certificate getCertificate() throws Exception {
		X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
		X500Principal dnName = new X500Principal("CN=Test");
		generator.setSubjectDN(dnName);
		generator.setIssuerDN(dnName); // use the same
		generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
		generator.setNotAfter(new Date(System.currentTimeMillis() + 24 * 365 * 24 * 60 * 60 * 1000));
		generator.setPublicKey(generateJWK_RSA().toRSAKey().toPublicKey());
		generator.setSignatureAlgorithm("SHA256WITHRSA");
		generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
		return generator.generate(generateJWK_RSA().toRSAKey().toPrivateKey());
	}
}
