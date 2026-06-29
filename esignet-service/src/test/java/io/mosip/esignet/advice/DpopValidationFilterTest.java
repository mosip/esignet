package io.mosip.esignet.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.*;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class DpopValidationFilterTest {

    @InjectMocks
    private DpopValidationFilter filter;

    @Mock
    private CacheUtilService cacheUtilService;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;
    private ECKey ecJwk;
    private String accessToken;

    @BeforeEach
    public void setup() throws Exception {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);

        ecJwk = new ECKeyGenerator(Curve.P_256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("key1")
                .generate();

        accessToken = generateAccessTokenForUserinfo(true);

        when(cacheUtilService.checkAndMarkJti(anyString(), anyLong())).thenReturn(false);
        ReflectionTestUtils.setField(filter, "discoveryMap", Map.ofEntries(
                Map.entry("dpop_signing_alg_values_supported", Arrays.asList("ES256", "RS256")),
                Map.entry("pushed_authorization_request_endpoint", "http://localhost/oauth/par"),
                Map.entry("token_endpoint", "http://localhost/oauth/token"),
                Map.entry("userinfo_endpoint", "http://localhost/oidc/userinfo")
        ));

        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(filter, "maxClockSkewSeconds", 10);
        ReflectionTestUtils.setField(filter, "maxDPOPIatAgeSeconds", 60);
    }

    private String createDpopJwtWithAllClaims(String httpMethod, String htuClaim, String accessToken, boolean withAth) throws Exception {
        String athHash = null;
        if (withAth) {
            athHash = IdentityProviderUtil.generateB64EncodedHash(IdentityProviderUtil.ALGO_SHA_256, accessToken);
        }

        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("dpop+jwt"))
                .jwk(ecJwk.toPublicJWK());

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", httpMethod)
                .claim("htu", htuClaim)
                .issueTime(Date.from(Instant.now()));

        if (withAth) {
            claimsBuilder.claim("ath", athHash);
        }

        SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claimsBuilder.build());

        JWSSigner signer = new ECDSASigner(ecJwk.toECPrivateKey());
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    private void addAuthorizationHeader(MockHttpServletRequest req, String accessToken) {
        req.addHeader("Authorization", "DPoP " + accessToken);
    }

    private String generateAccessTokenForUserinfo(boolean addCnfClaim) throws Exception {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject("test-user")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)));

        if (addCnfClaim) {
            claimsBuilder.claim("cnf", Map.of("jkt", ecJwk.toPublicJWK().computeThumbprint().toString()));
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).build(),
                claimsBuilder.build()
        );

        signedJWT.sign(new ECDSASigner(ecJwk.toECPrivateKey()));
        return signedJWT.serialize();
    }

    @Test
    public void testDpopHeader_withUserinfoPathAndAthClaim_thenPass() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/oidc/userinfo", accessToken, true);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testDpopHeader_replayDetection_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/oidc/userinfo", accessToken, true);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        when(cacheUtilService.checkAndMarkJti(anyString(), anyLong())).thenReturn(true); // simulate replay

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testDpopHeader_mismatchedHttpMethodInHtmClaim_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("POST", "http://localhost/oidc/userinfo", accessToken, true);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testDpopHeader_withUserinfoPathAndWithoutAthClaim_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/oidc/userinfo", accessToken, false); // ath missing

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testMalformedHtuClaimMismatch_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/wrongpath", accessToken, true);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testHtuClaimWithQueryParamsAndFragment_thenPass() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/oidc/userinfo?q1=abc&q2=xyz#frag", accessToken, true);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testDpopHeader_withEmptyJwk_thenFail() throws Exception {
        String headerJson = "{"
                + "\"alg\":\"ES256\","
                + "\"typ\":\"dpop+jwt\","
                + "\"jwk\":{}"
                + "}";
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", "GET")
                .claim("htu", "http://localhost/oidc/userinfo")
                .issueTime(Date.from(Instant.now()))
                .build();

        String claimsJson = claims.toPayload().toString();

        String encodedHeader = Base64URL.encode(headerJson).toString();
        String encodedClaims = Base64URL.encode(claimsJson).toString();
        String encodedSignature = Base64URL.encode("invalid-signature").toString();
        String dpopJwt = encodedHeader + "." + encodedClaims + "." + encodedSignature;

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");
        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testDpopHeader_withUserinfoPathAndWithoutDpopHeaderAndCnfClaim_thenPass() throws Exception {
        String accessTokenWithoutCnf = generateAccessTokenForUserinfo(false);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("Authorization", "Bearer " + accessTokenWithoutCnf);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testDpopHeader_withUserinfoPathAndWithoutDpopHeaderAndAccessTokenWithCnfClaim_thenFail() throws Exception {
        request.setRequestURI("/oidc/userinfo");
        request.addHeader("Authorization", "Bearer " + accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_AUTH_TOKEN));
    }

    @Test
    public void testDpopHeader_withUserinfoPathDpopHeaderAndWithoutAuthHeader_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/oidc/userinfo", accessToken, true);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_AUTH_TOKEN));
    }

    @Test
    public void testDpopHeader_withUserinfoPathAndAccessTokenWithoutCnfClaim_thenFail() throws Exception {
        accessToken = generateAccessTokenForUserinfo(false);
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/oidc/userinfo", accessToken, true);
        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
    }

    @Test
    public void testDpopHeader_withPushedAuthorizationRequestPath_thenPass() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("POST", "http://localhost/oauth/par", null, false);
        request.setRequestURI("/oauth/par");
        request.addHeader("DPoP", dpopJwt);
        request.setMethod("POST");
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testUserinfoPath_withoutAuthorizationHeader_thenFail() throws Exception {
        request.setRequestURI("/oidc/userinfo");
        // No Authorization header added
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        // Should not call filterChain.doFilter due to error
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        String wwwAuthenticate = response.getHeader("WWW-Authenticate");
        assertNotNull(wwwAuthenticate);
        assertTrue(wwwAuthenticate.contains("error=\"invalid_token\""));
    }

    @Test
    public void testDpopHeader_withIatOutsideTimeLimit_thenFail() throws Exception {
        String athHash = IdentityProviderUtil.generateB64EncodedHash(IdentityProviderUtil.ALGO_SHA_256, accessToken);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("dpop+jwt"))
                .jwk(ecJwk.toPublicJWK())
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", "GET")
                .claim("htu", "http://localhost/oidc/userinfo")
                .claim("ath", athHash)
                .issueTime(Date.from(Instant.now().plusSeconds(20))) // iat in the future beyond clock skew
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new ECDSASigner(ecJwk.toECPrivateKey()));
        String dpopJwt = signedJWT.serialize();

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());

        claims = new JWTClaimsSet.Builder(claims).issueTime(Date.from(Instant.now().minusSeconds(80))).build();
        signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new ECDSASigner(ecJwk.toECPrivateKey()));
        dpopJwt = signedJWT.serialize();

        request.removeHeader("DPoP");
        request.addHeader("DPoP", dpopJwt);
        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testUserinfoPath_withMultipleAuthorizationHeaders_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/oidc/userinfo", accessToken, true);

        request.setRequestURI("/oidc/userinfo");
        request.addHeader("DPoP", dpopJwt);
        // Add two Authorization headers
        addAuthorizationHeader(request, accessToken);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        // Should not call filterChain.doFilter due to error
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(400, response.getStatus());
        String wwwAuthenticate = response.getHeader("WWW-Authenticate");
        assertNotNull(wwwAuthenticate);
        assertTrue(wwwAuthenticate.contains("error=\"invalid_request\""));
    }

    /**
     * Builds a DPoP proof JWT with a caller-controlled iat so we can drive the
     * iat-anchored TTL formula in {@code replayCheck}.
     */
    private String createDpopJwtWithCustomIat(String httpMethod, String htuClaim, Instant iat) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("dpop+jwt"))
                .jwk(ecJwk.toPublicJWK())
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", httpMethod)
                .claim("htu", htuClaim)
                .issueTime(Date.from(iat))
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new ECDSASigner(ecJwk.toECPrivateKey()));
        return signedJWT.serialize();
    }

    /**
     * Happy path — verifies that the TTL handed to the JTI cache equals
     * {@code maxDPOPIatAgeSeconds + 2 * maxClockSkewSeconds} when iat = now.
     * With the configured defaults (60 + 2*10), the expected TTL is ~80s.
     */
    @Test
    public void testDpopHeader_replayCheck_capturesIatAnchoredTtl() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("POST", "http://localhost/oauth/par", null, false);

        request.setRequestURI("/oauth/par");
        request.addHeader("DPoP", dpopJwt);
        request.setMethod("POST");

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        when(cacheUtilService.checkAndMarkJti(anyString(), ttlCaptor.capture())).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        long ttl = ttlCaptor.getValue();
        // iat ≈ now ⇒ expirySec = now + 60 + 20 ⇒ TTL ≈ 80s (allow ±2s for scheduling jitter)
        assertTrue(ttl >= 78 && ttl <= 80,
                "Expected TTL ~80s (maxDPOPIatAgeSeconds + 2*maxClockSkewSeconds), got " + ttl);
    }

    /**
     * Confirms TTL is anchored to {@code iat}, not to "now":
     * a proof issued 30s ago must shrink the cache entry's lifetime by ~30s.
     * Expected TTL = (iat - 30) + 60 + 20 - now  ≈  50s.
     */
    @Test
    public void testDpopHeader_replayCheck_olderIat_shortensTtl() throws Exception {
        String dpopJwt = createDpopJwtWithCustomIat("POST", "http://localhost/oauth/par",
                Instant.now().minusSeconds(30));

        request.setRequestURI("/oauth/par");
        request.addHeader("DPoP", dpopJwt);
        request.setMethod("POST");

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        when(cacheUtilService.checkAndMarkJti(anyString(), ttlCaptor.capture())).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        long ttl = ttlCaptor.getValue();
        // expirySec = (now-30) + 60 + 20 = now + 50 ⇒ TTL ≈ 50s
        assertTrue(ttl >= 48 && ttl <= 50,
                "Expected TTL ~50s for iat=now-30s, got " + ttl);
    }

}
