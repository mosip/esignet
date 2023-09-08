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
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("HTTPS://DEV.MOSIP.NET/home"),
                "https://dev.mosip.net/home");
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
    public void test_generateThumbprintByCertificate()throws Exception{
        String certificateString="-----BEGIN CERTIFICATE-----\nMIIDYjCCAkqgAwIBAgIIyF+UqkzoF9kwDQYJKoZIhvcNAQELBQAwgYIxCzAJBgNV\nBAYTAklOMQswCQYDVQQIDAJLQTESMBAGA1UEBwwJQkFOR0FMT1JFMQ0wCwYDVQQK\nDARJSVRCMSwwKgYDVQQLDCNNT1NJUC1URUNILUNFTlRFUiAoSURBX0tFWV9CSU5E\nSU5HKTEVMBMGA1UEAwwMd3d3Lm1vc2lwLmlvMB4XDTIzMDgyMjEyMTU1OVoXDTIz\nMTIyMDEyMTU1OVowGzEZMBcGA1UEAwwQVEVTVF9GVUxMTkFNRWVuZzCCASIwDQYJ\nKoZIhvcNAQEBBQADggEPADCCAQoCggEBANR6slnf+yDgQ8Z2oU5wcV7c7YEGx4J2\nk6RL5KxvigISwrxy3d7ZwdJvASuolzOSdspvAf32EoLjlMgCPCAUJ5VacbPa0YgX\n8srYwBdEzgecTorU9XGDQoAnAaI28JEDtQbuZJ8dMiwYk//g8QMj2xs1sw1t1A3q\n4lAfH4UxnmBNipq2p7wtn7PdOjq2TfKUkNDYx7hNbNQe75Z1KaXfN1mr02k7V0F+\nFgKScaBXtxWFZt6OoUdJQixTF9vz13Skl+J+/sCP3lyC4OptAbD0chYrvXlsAMDj\n9r0OXFPzT9pchi4ZojgGhu9HXV1REadfvVSyeysv2VSHGvuooKiyik0CAwEAAaNC\nMEAwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUV7X8HklamaYsSF8SKH3YlCQ7\nop4wDgYDVR0PAQH/BAQDAgKEMA0GCSqGSIb3DQEBCwUAA4IBAQC+eRZJoeBFyK5P\niOSnaSeXk6hFyF9tgh7QAXho/5nJ4QgYwU+16GjatMZNuYh5VFjmrmuf7fVnHI5R\nzGj+D4T3F78CB2B5l/Fh3v73C7fU8G6th5Le4aLhpEtOOqAYpx99uiDNs07+Up59\nHdYDQrX6k9IThAa+JrYjllShGJoPJYrRFOo/amBTFjQnKxFQ1IvUfBsuqalIGulh\n4K/5H4lC8aC9U2LaP+Ncu6six/MF/OiNurF86F9uAQucuxsay7hZSZk5I+iGGkcl\nGAJCVFB37kXviV4n4m6o/YPw/xeZ4tuRlFZ7+KkWekcXt7QwdNiCpmPrPYIshTYh\n5DMsAUrK\n-----END CERTIFICATE-----\n";
        Assert.assertNotNull(IdentityProviderUtil.generateCertificateThumbprint(certificateString));
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
