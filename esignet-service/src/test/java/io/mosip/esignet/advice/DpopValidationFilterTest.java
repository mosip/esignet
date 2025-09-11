package io.mosip.esignet.advice;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.*;
import io.mosip.esignet.core.constants.ErrorConstants;
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

    @Mock
    private Map<String, Object> discoveryMap;

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

        Map<String, Object> supportedAlgorithms = new HashMap<>();
        supportedAlgorithms.put("dpop_signing_alg_values_supported", Arrays.asList("ES256", "RS256"));
        when(discoveryMap.get("dpop_signing_alg_values_supported")).thenReturn(Arrays.asList("ES256", "RS256"));
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

        String normalizedUri = new URI(uri).normalize().toString();
        String htuClaim = new URI(
                new URI(normalizedUri).getScheme().toLowerCase(),
                new URI(normalizedUri).getAuthority().toLowerCase(),
                new URI(normalizedUri).getPath(),
                null,
                null
        ).toString();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .claim("htm", httpMethod)
                .claim("htu", htuClaim)
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
        req.addHeader("Authorization", "DPoP " + accessToken);
    }

    @Test
    public void testValidDpopHeaderWithUserinfoPathIncludingAthClaim_thenPass() throws Exception {
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
    public void testReplayDetection_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/userinfo", accessToken, true);

        request.setRequestURI("/userinfo");
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
    public void testMismatchedHttpMethodInHtmClaim_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("POST", "http://localhost/userinfo", accessToken, true);

        request.setRequestURI("/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void testMissingAthClaimOnUserinfoPath_thenFail() throws Exception {
        String dpopJwt = createDpopJwtWithAllClaims("GET", "http://localhost/userinfo", accessToken, false); // ath missing

        request.setRequestURI("/userinfo");
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

        request.setRequestURI("/userinfo");
        request.addHeader("DPoP", dpopJwt);
        addAuthorizationHeader(request, accessToken);
        request.setMethod("GET");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains(ErrorConstants.INVALID_DPOP_PROOF));
        verify(filterChain, never()).doFilter(any(), any());
    }

}
