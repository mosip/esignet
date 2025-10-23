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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
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

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);

        ecJwk = new ECKeyGenerator(Curve.P_256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("key1")
                .generate();

        accessToken = generateAccessTokenForUserinfo(true);

        when(cacheUtilService.checkAndMarkJti(anyString())).thenReturn(false);
        ReflectionTestUtils.setField(filter, "discoveryMap", Map.ofEntries(
                Map.entry("dpop_signing_alg_values_supported", Arrays.asList("ES256", "RS256")),
                Map.entry("pushed_authorization_request_endpoint", "http://localhost/oauth/par"),
                Map.entry("token_endpoint", "http://localhost/oauth/token"),
                Map.entry("userinfo_endpoint", "http://localhost/oidc/userinfo")
        ));

        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
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
            claimsBuilder.claim("cnf", Map.of("jkt", ecJwk.toPublicJWK().computeThumbprint()));
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

        when(cacheUtilService.checkAndMarkJti(anyString())).thenReturn(true); // simulate replay

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

        String claimsJson = claims.toJSONObject().toJSONString();

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

}
