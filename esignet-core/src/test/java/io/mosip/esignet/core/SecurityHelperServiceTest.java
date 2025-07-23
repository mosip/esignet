/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.Base64URL;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.SecurityHelperService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.Assert;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class SecurityHelperServiceTest {

    SecurityHelperService securityHelperService = new SecurityHelperService();


    @Test
    public void test_generateSecureRandomString_thenPass() {
        Assert.notNull(securityHelperService.generateSecureRandomString(20));
    }

    @Test
    public void computeJwkThumbprint_withNullKey_thenFail() throws Exception {
        try {
            securityHelperService.computeJwkThumbprint(null);
        }catch (IllegalArgumentException e){
            org.junit.Assert.assertEquals( ErrorConstants.INVALID_ALGORITHM,e.getMessage());
        }
    }

    @Test
    public void computeJwkThumbprint_withPrivateKey_thenFail() throws Exception {
        // Provide private key to simulate the private key rejection
        RSAKey privateRsaKey = new RSAKey.Builder(new TestRSAPublicKey())
                .privateExponent(Base64URL.encode(BigInteger.TEN)) // Simulate private key
                .build();
        try {
            securityHelperService.computeJwkThumbprint(privateRsaKey);
        }catch (IllegalArgumentException e){
            org.junit.Assert.assertEquals( ErrorConstants.INVALID_PUBLIC_KEY,e.getMessage());
        }
    }

    @Test
    public void computeJwkThumbprint_withValidRSA_thenPass() throws Exception {
        ReflectionTestUtils.setField(securityHelperService, "objectMapper", new ObjectMapper());
        RSAKey rsaKey = new RSAKey.Builder(new TestRSAPublicKey()).build();
        String thumbprint = securityHelperService.computeJwkThumbprint(rsaKey);
        assertNotNull(thumbprint);
    }


    @Test
    public void computeJwkThumbprint_withInvalidRSA_thenFail() throws Exception {
        JWK fakeKey = new OctetSequenceKey.Builder(new Base64URL("AQAB")).build();
        try {
            securityHelperService.computeJwkThumbprint(fakeKey);
        }catch (IllegalArgumentException e){
            org.junit.Assert.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY,e.getMessage());
        }
    }

    @Test
    public void computeJwkThumbprint_withSymmetricKey_thenFail() throws Exception {
        String octJwkJson = "{ \"kty\": \"oct\", \"k\": \"secret\" }";
        JWK jwk = JWK.parse(octJwkJson);
        try {
            securityHelperService.computeJwkThumbprint(jwk);
        }catch (IllegalArgumentException e){
            org.junit.Assert.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY,e.getMessage());
        }
    }

    @Test
    public void computeJwkThumbprint_withUnsupportedKey_thenFail() throws Exception {
        String unsupportedJwkJson = "{ \"kty\": \"OKP\", \"crv\": \"Ed25519\", \"x\": \"testx\" }";
        JWK jwk = JWK.parse(unsupportedJwkJson);
        try {
            securityHelperService.computeJwkThumbprint(jwk);
        }catch (IllegalArgumentException e){
            org.junit.Assert.assertEquals(ErrorConstants.INVALID_ALGORITHM,e.getMessage());
        }
    }

    // Minimal RSA public key for testing
    static class TestRSAPublicKey implements RSAPublicKey {
        public BigInteger getPublicExponent() { return BigInteger.valueOf(65537); }
        public BigInteger getModulus() { return new BigInteger("1234567890"); }
        public String getAlgorithm() { return "RSA"; }
        public String getFormat() { return "X.509"; }
        public byte[] getEncoded() { return new byte[0]; }
    }

}
