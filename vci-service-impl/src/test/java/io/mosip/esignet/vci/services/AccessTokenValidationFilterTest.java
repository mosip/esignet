/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.vci.filter.AccessTokenValidationFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AccessTokenValidationFilterTest {

    private static MockWebServer mockWebServer;

    @BeforeClass
    public static void startWebServerConnection() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(InetAddress.getLoopbackAddress(), 4501);
    }

    @AfterClass
    public static void closeWebServerConnection() throws IOException {
        if(mockWebServer != null) {
            mockWebServer.close();
            mockWebServer = null;
        }
    }

    @Test
    public void testFilter_withNoBearerAccessToken_thenFail() throws Exception {
        AccessTokenValidationFilter accessTokenValidationFilter = new AccessTokenValidationFilter();
        ReflectionTestUtils.setField(accessTokenValidationFilter, "urlPatterns", List.of("/credential"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "allowedAudiences", List.of("test-audience"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "issuerUri", "");
        ReflectionTestUtils.setField(accessTokenValidationFilter, "jwkSetUri", "");

        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        ReflectionTestUtils.setField(accessTokenValidationFilter, "parsedAccessToken", parsedAccessToken);

        MockMvcBuilders.standaloneSetup(new CredentialController())
                .addFilter(accessTokenValidationFilter).build()
                .perform(get("/credential"))
                .andExpect(status().isOk());

        Assert.assertFalse(parsedAccessToken.isActive());
        Assert.assertNull(parsedAccessToken.getAccessTokenHash());
        Assert.assertNull(parsedAccessToken.getClaims());
    }

    @Test
    public void testFilter_withValidAccessToken_thenPass() throws Exception {
        AccessTokenValidationFilter accessTokenValidationFilter = new AccessTokenValidationFilter();
        ReflectionTestUtils.setField(accessTokenValidationFilter, "urlPatterns", List.of("/credential"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "allowedAudiences", List.of("test-audience"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "issuerUri", "test-issuer");
        ReflectionTestUtils.setField(accessTokenValidationFilter, "jwkSetUri", "http://127.0.0.1:4501/oauth/jwks.json");

        RSAKey rsaKey = generateRsaKey();
        String accessToken = buildAccessToken(rsaKey, Date.from(Instant.now(Clock.systemUTC())),
                Date.from(Instant.now(Clock.systemUTC()).plusSeconds(200)));
        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody("{\"keys\":["+rsaKey.toPublicJWK().toJSONString()+"]}");
        mockWebServer.enqueue(mockResponse);

        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(false);
        ReflectionTestUtils.setField(accessTokenValidationFilter, "parsedAccessToken", parsedAccessToken);

        MockMvcBuilders.standaloneSetup(new CredentialController())
                .addFilter(accessTokenValidationFilter).build()
                .perform(get("/credential")
                        .header("authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        Assert.assertTrue(parsedAccessToken.isActive());
        Assert.assertNotNull(parsedAccessToken.getAccessTokenHash());
        Assert.assertNotNull(parsedAccessToken.getClaims());
        Assert.assertEquals("test-issuer", parsedAccessToken.getClaims().get("iss"));
    }

    @Test
    public void testFilter_withExpiredAccessToken_thenFail() throws Exception {
        AccessTokenValidationFilter accessTokenValidationFilter = new AccessTokenValidationFilter();
        ReflectionTestUtils.setField(accessTokenValidationFilter, "urlPatterns", List.of("/credential"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "allowedAudiences", List.of("test-audience"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "issuerUri", "test-issuer");
        ReflectionTestUtils.setField(accessTokenValidationFilter, "jwkSetUri", "http://127.0.0.1:4501/oauth/jwks.json");

        RSAKey rsaKey = generateRsaKey();
        String accessToken = buildAccessToken(rsaKey, Date.from(Instant.now(Clock.systemUTC())),
                Date.from(Instant.now(Clock.systemUTC())));
        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody("{\"keys\":["+rsaKey.toPublicJWK().toJSONString()+"]}");
        mockWebServer.enqueue(mockResponse);

        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        ReflectionTestUtils.setField(accessTokenValidationFilter, "parsedAccessToken", parsedAccessToken);

        MockMvcBuilders.standaloneSetup(new CredentialController())
                .addFilter(accessTokenValidationFilter).build()
                .perform(get("/credential")
                        .header("authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        Assert.assertFalse(parsedAccessToken.isActive());
        Assert.assertNull(parsedAccessToken.getAccessTokenHash());
        Assert.assertNull(parsedAccessToken.getClaims());
    }

    @Test
    public void testFilter_withInvalidClaims_thenFail() throws Exception {
        AccessTokenValidationFilter accessTokenValidationFilter = new AccessTokenValidationFilter();
        ReflectionTestUtils.setField(accessTokenValidationFilter, "urlPatterns", List.of("/credential"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "allowedAudiences", List.of("mock-audience"));
        ReflectionTestUtils.setField(accessTokenValidationFilter, "issuerUri", "mock-issuer");
        ReflectionTestUtils.setField(accessTokenValidationFilter, "jwkSetUri", "http://127.0.0.1:4501/oauth/jwks.json");

        RSAKey rsaKey = generateRsaKey();
        String accessToken = buildAccessToken(rsaKey, Date.from(Instant.now(Clock.systemUTC())),
                Date.from(Instant.now(Clock.systemUTC()).plusSeconds(60)));
        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody("{\"keys\":["+rsaKey.toPublicJWK().toJSONString()+"]}");
        mockWebServer.enqueue(mockResponse);

        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        ReflectionTestUtils.setField(accessTokenValidationFilter, "parsedAccessToken", parsedAccessToken);

        MockMvcBuilders.standaloneSetup(new CredentialController())
                .addFilter(accessTokenValidationFilter).build()
                .perform(get("/credential")
                        .header("authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        Assert.assertFalse(parsedAccessToken.isActive());
        Assert.assertNull(parsedAccessToken.getAccessTokenHash());
        Assert.assertNull(parsedAccessToken.getClaims());
    }

    private String buildAccessToken(RSAKey rsaKey, Date iat, Date exp) throws JOSEException {
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .jwk(rsaKey.toPublicJWK())
                .build();
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject("test-subject")
                .issuer("test-issuer")
                .audience("test-audience")
                .issueTime(iat)
                .expirationTime(exp)
                .claim("client_id", "client_id")
                .build();

        SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
        signedJWT.sign(new RSASSASigner(rsaKey));
        return signedJWT.serialize();
    }

    private RSAKey generateRsaKey() {
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
        } catch (Exception e) {}
        return null;
    }

    @RestController
    private static class CredentialController {
        @GetMapping(path = "/credential")
        public String getCredential() {
            return "successfully created the VC";
        }
    }
}
