package io.mosip.esignet.advice;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.*;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.InvalidDPoPHeaderException;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

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

        accessToken = "sampleaccesstoken";

        when(cacheUtilService.checkAndMarkJti(anyString())).thenReturn(false);
    }

    private String createDpopJwtWithAllClaims(String httpMethod, String uri, String accessToken, boolean withAth) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String athHash = null;
        if (withAth) {
            byte[] hashedToken = digest.digest(accessToken.getBytes(StandardCharsets.UTF_8));
            athHash = Base64URL.encode(hashedToken).toString();
        }

        Base64URL thumbprint = ecJwk.toPublicJWK().computeThumbprint();

        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("dpop+jwt"))
                .jwk(ecJwk.toPublicJWK());

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", httpMethod)
                .claim("htu", new URI(uri).normalize().toString())
                .issueTime(Date.from(Instant.now()))
                .claim("cnf", Collections.singletonMap("jkt", thumbprint.toString()));

        if (withAth) {
            claimsBuilder.claim("ath", athHash);
        }

        SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claimsBuilder.build());

        JWSSigner signer = new ECDSASigner(ecJwk.toECPrivateKey());
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    private void addAuthorizationHeader(MockHttpServletRequest req, String accessToken) {
        req.addHeader("Authorization", "Bearer " + accessToken);
    }

    @Test
    public void testValidDpopHeaderWithUserinfoPathIncludingAthClaim() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/userinfo", accessToken, true);

        request.setRequestURI("/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testValidDpopHeaderNonUserinfoPathWithoutAthClaim() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/token", accessToken, false);

        request.setRequestURI("/token");
        request.addHeader("DPoP", dpopJwt);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testReplayDetectionReturns401() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/userinfo", accessToken, true);

        request.setRequestURI("/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        when(cacheUtilService.checkAndMarkJti(anyString())).thenReturn(true); // simulate replay

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testCheckRequiredClaims_missingClaim_throws() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", "GET")
                .claim("htu", "http://localhost/userinfo")
                // iat claim omitted on purpose
                .claim("cnf", Collections.singletonMap("jkt", "someThumbprint"))
                .build();

        Method method = DpopValidationFilter.class.getDeclaredMethod("checkRequiredClaims", JWTClaimsSet.class);
        method.setAccessible(true);

        try {
            method.invoke(filter, claims);
            fail("Expected InvalidDPoPHeaderException");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof InvalidDPoPHeaderException);
            assertEquals(ErrorConstants.INVALID_DPOP_HEADER, cause.getMessage());
        }
    }

    @Test
    public void testMismatchedHttpMethodInHtmClaim() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("POST", "http://localhost/userinfo", accessToken, true);

        request.setRequestURI("/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testMissingAthClaimOnUserinfoPath() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/userinfo", accessToken, false); // ath missing

        request.setRequestURI("/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testMalformedHtuClaimMismatch() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/wrongpath", accessToken, true);

        request.setRequestURI("/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

}
