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

import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.Assert;
import org.junit.Test;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.util.IdentityProviderUtil;

public class IdentityProviderUtilTest {


    @Test
    public void validateRedirectURIPositiveTest() throws IdPException {
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
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home**"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}
        
        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("test-url"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}
    }
    
    @Test
    public void test_dateTime() {
    	Assert.assertNotNull(IdentityProviderUtil.getUTCDateTime());
    	Assert.assertNotNull(IdentityProviderUtil.getUTCDateTimeWithNanoSeconds());
    	Assert.assertNotNull(IdentityProviderUtil.getEpochSeconds());
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
        } catch (IdPException e) {}
    }
    
    @Test
    public void test_generateB64EncodedHash() {
    	Assert.assertNotNull(IdentityProviderUtil.generateB64EncodedHash("sha-256", "test-b64-hash"));
    	try {
    		IdentityProviderUtil.generateB64EncodedHash("test-algorithm", "test");
            Assert.fail();
        } catch (IdPException e) {}
    }
    
    @Test
    public void test_encodeDecode() {
    	IdentityProviderUtil.b64Encode("test-encode-string");
    	IdentityProviderUtil.b64Encode("test-bytes".getBytes());
    	IdentityProviderUtil.b64Decode("test-decode-string");
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
        } catch (IdPException e) {}
    }
    
    @Test
    public void test_getCertificateThumbprint() throws Exception {
    	Assert.assertNotNull(IdentityProviderUtil.getCertificateThumbprint("SHA-256", getCertificate()));
    	try {
    		IdentityProviderUtil.getCertificateThumbprint("test", getCertificate());
    		Assert.fail();
        } catch (IdPException e) {}
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
