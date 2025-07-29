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
            org.junit.Assert.assertEquals( ErrorConstants.INVALID_PUBLIC_KEY,e.getMessage());
        }
    }

    @Test
    public void computeJwkThumbprint_withPrivateKey_thenFail() throws Exception {
        Base64URL n = Base64URL.from("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw");
        Base64URL e = Base64URL.from("AQAB");
        RSAKey privateRsaKey = new RSAKey.Builder(n, e)
                .privateExponent(Base64URL.encode(BigInteger.TEN))
                .build();

        try {
            securityHelperService.computeJwkThumbprint(privateRsaKey);
            org.junit.Assert.fail();
        } catch (IllegalArgumentException ex) {
            org.junit.Assert.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, ex.getMessage());
        }
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

    @Test
    public void computeJwkThumbprint_withValidRSA_thenPass() throws Exception {
        ReflectionTestUtils.setField(securityHelperService, "objectMapper", new ObjectMapper());
        RSAKey rsaJwk = new RSAKey.Builder(
                Base64URL.from("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"),
                Base64URL.from("AQAB")
        ).build();

        String thumbprint = securityHelperService.computeJwkThumbprint(rsaJwk);

        String expectedThumbprint = "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs";

        org.junit.Assert.assertEquals(expectedThumbprint, thumbprint);
    }

    @Test
    public void computeJwkThumbprint_withValidEC_thenPass() throws Exception {
        ReflectionTestUtils.setField(securityHelperService, "objectMapper", new ObjectMapper());
        ECKey ecJwk = ECKey.parse(
                "{" +
                        "\"kty\":\"EC\"," +
                        "\"crv\":\"P-256\"," +
                        "\"x\":\"CtFRxfPrv4fh4c8gGzBveGvk8VkJIQLwMuEBPh81xnk\"," +
                        "\"y\":\"uAyPt9YJWwqaaQEYkHS1KIGvzVe3T5NGDqM3Bm4qLUs\"" +
                        "}"
        );

        String thumbprint = securityHelperService.computeJwkThumbprint(ecJwk);
        String expected = "7BWWOB0woOhbkjAvfme5xwdOsehUXM_dZsy8ZLPEvss";

        org.junit.Assert.assertEquals(expected, thumbprint);
    }

}
