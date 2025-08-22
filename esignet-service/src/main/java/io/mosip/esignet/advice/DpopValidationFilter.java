package io.mosip.esignet.advice;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import io.mosip.esignet.core.exception.InvalidDPoPHeaderException;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;

@Slf4j
@ControllerAdvice
public class DpopValidationFilter extends OncePerRequestFilter {

    private static final long MAX_CLOCK_SKEW_SECONDS = 300L;

    private static final Set<String> REQUIRED_CLAIMS = Set.of("htm", "htu", "iat", "jti", "cnf");

    private static final Set<String> SUPPORTED_ALGORITHMS = Set.of("ES256", "RS256");

    @Autowired
    private CacheUtilService cacheUtilService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!(uri.startsWith("/userinfo") || uri.startsWith("/par") || uri.startsWith("/token"))) {
            // Skip validation for all other requests
            filterChain.doFilter(request, response);
            return;
        }

        Enumeration<String> dpopHeaders = request.getHeaders("DPoP");
        List<String> dpopHeaderList = Collections.list(dpopHeaders);

        if (dpopHeaderList.isEmpty()) {
//            log.error("Missing DPoP header");
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing DPoP header");
            return;
        }
        if (dpopHeaderList.size() != 1) {
            log.error("Multiple DPoP headers present");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Multiple DPoP headers present");
            return;
        }

        String dpopHeader = dpopHeaderList.get(0);

        try {
            if (request.getRequestURI().startsWith("/userinfo")) {
                validateUserInfoDpop(dpopHeader, request);
            } else if (request.getRequestURI().startsWith("/par") || request.getRequestURI().startsWith("/token")) {
                validateParOrTokenDpop(dpopHeader, request);
            }
            // else skip or allow through
           // filterChain.doFilter(request, response);
        } catch (InvalidDPoPHeaderException ex) {
            log.error("DPoP validation failed: {}", ex.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid DPoP proof: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected DPoP validation error", ex);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid DPoP proof");
        }
    }

    // Validation for /par and /token endpoints: general validation only
    private void validateParOrTokenDpop(String dpopJwt, HttpServletRequest request) throws ParseException {
        SignedJWT jwt = parseAndValidateHeader(dpopJwt);

        JWK jwk = jwt.getHeader().getJWK();
        verifySignature(jwt, jwk);

        JWTClaimsSet claims = getClaims(jwt);

        checkRequiredClaims(claims);

        verifyClaimsBasicValues(claims, request);

        replayCheck(claims);

        checkCnfClaimPresent(claims);
    }

    // Validation for /userinfo endpoint: includes ath & cnf with jkt only
    private void validateUserInfoDpop(String dpopJwt, HttpServletRequest request) throws ParseException {
        SignedJWT jwt = parseAndValidateHeader(dpopJwt);

        JWK jwk = jwt.getHeader().getJWK();
        verifySignature(jwt, jwk);

        JWTClaimsSet claims = getClaims(jwt);

        checkRequiredClaims(claims);

        verifyClaimsBasicValues(claims, request);

        replayCheck(claims);

        checkCnfJktClaim(claims, jwk);

        validateAthClaim(claims, request);
    }

    private SignedJWT parseAndValidateHeader(String dpopJwt) {
        try {
            SignedJWT jwt = SignedJWT.parse(dpopJwt);

            JWSHeader header = jwt.getHeader();
            if (header == null) {
                throw new InvalidDPoPHeaderException("Missing JWT header");
            }
            if (header.getType() == null || !"dpop+jwt".equalsIgnoreCase(header.getType().toString())) {
                throw new InvalidDPoPHeaderException("Invalid typ header: expected dpop+jwt");
            }

            String alg = header.getAlgorithm() != null ? header.getAlgorithm().getName() : null;
            if (alg == null || "none".equalsIgnoreCase(alg) || !SUPPORTED_ALGORITHMS.contains(alg)) {
                throw new InvalidDPoPHeaderException("Invalid or unsupported alg header");
            }

            JWK jwk = header.getJWK();
            if (jwk == null) {
                throw new InvalidDPoPHeaderException("Missing jwk header");
            }
            if (jwk.isPrivate()) {
                throw new InvalidDPoPHeaderException("JWK must not contain private key material");
            }

            return jwt;

        } catch (ParseException e) {
            throw new InvalidDPoPHeaderException("Failed to parse DPoP JWT: " + e.getMessage());
        }
    }

    private void verifySignature(SignedJWT jwt, JWK jwk) {
        try {
            JWSVerifier verifier;
            if (jwk instanceof RSAKey) {
                verifier = new RSASSAVerifier(((RSAKey) jwk).toRSAPublicKey());
            } else if (jwk instanceof ECKey) {
                verifier = new ECDSAVerifier((ECKey) jwk);
            } else {
                throw new InvalidDPoPHeaderException("Unsupported JWK key type");
            }
            if (!jwt.verify(verifier)) {
                throw new InvalidDPoPHeaderException("DPoP JWT signature verification failed");
            }
        } catch (JOSEException e) {
            throw new InvalidDPoPHeaderException("DPoP signature verification error: " + e.getMessage());
        }
    }

    private JWTClaimsSet getClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new InvalidDPoPHeaderException("Failed to get JWT claims: " + e.getMessage());
        }
    }

    private void checkRequiredClaims(JWTClaimsSet claims) {
        for (String required : REQUIRED_CLAIMS) {
            if (claims.getClaim(required) == null) {
                throw new InvalidDPoPHeaderException("Missing required claim: " + required);
            }
        }
    }

    private void verifyClaimsBasicValues(JWTClaimsSet claims, HttpServletRequest request) throws ParseException {
        String htm = claims.getStringClaim("htm");
        if (!request.getMethod().equalsIgnoreCase(htm)) {
            throw new InvalidDPoPHeaderException("htm claim does not match HTTP method");
        }

        // Compare htu URI ignoring query and fragment only, simple exact string without normalization
        String htu = claims.getStringClaim("htu");
        String reqUri;
        try {
            reqUri = getRequestUriWithoutQuery(request);
        } catch (URISyntaxException e) {
            throw new InvalidDPoPHeaderException("Invalid request URI: " + e.getMessage());
        }

        if (!htu.equals(reqUri)) {
            throw new InvalidDPoPHeaderException("htu claim does not match request URI");
        }

        Date iatDate = claims.getIssueTime();
        if (iatDate == null) {
            throw new InvalidDPoPHeaderException("Missing iat claim");
        }
        Instant iat = iatDate.toInstant();
        Instant now = Instant.now();
        if (Math.abs(now.getEpochSecond() - iat.getEpochSecond()) > MAX_CLOCK_SKEW_SECONDS) {
            throw new InvalidDPoPHeaderException("iat claim is outside acceptable window");
        }
    }

    private void replayCheck(JWTClaimsSet claims) {
        String jti = claims.getJWTID();
        if (jti == null || jti.isEmpty()) {
            throw new InvalidDPoPHeaderException("Missing jti claim");
        }
        if (cacheUtilService.checkAndMarkJti(jti)) {
            throw new InvalidDPoPHeaderException("Replay detected for jti: " + jti);
        }
    }

    private void checkCnfClaimPresent(JWTClaimsSet claims) {
        Object cnf = claims.getClaim("cnf");
        if (cnf == null) {
            throw new InvalidDPoPHeaderException("Missing cnf claim");
        }
    }

    // Userinfo specific: only allow cnf with jkt (thumbprint) validation
    private void checkCnfJktClaim(JWTClaimsSet claims, JWK proofJwk) {
        Object obj = claims.getClaim("cnf");
        if (!(obj instanceof Map)) {
            throw new InvalidDPoPHeaderException("Invalid cnf claim");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cnfMap = (Map<String, Object>) obj;

        if (!cnfMap.containsKey("jkt")) {
            throw new InvalidDPoPHeaderException("cnf claim must contain 'jkt' for userinfo");
        }
        String jktVal = cnfMap.get("jkt").toString();

        try {
            String actualThumbprint = proofJwk.computeThumbprint().toString();
            if (!jktVal.equals(actualThumbprint)) {
                throw new InvalidDPoPHeaderException("cnf jkt does not match proof key thumbprint");
            }
        } catch (JOSEException e) {
            throw new InvalidDPoPHeaderException("Failed to compute proof key thumbprint: " + e.getMessage());
        }
    }

    private void validateAthClaim(JWTClaimsSet claims, HttpServletRequest request) throws ParseException {
        String ath = claims.getStringClaim("ath");
        if (ath == null || ath.isEmpty()) {
            throw new InvalidDPoPHeaderException("Missing ath claim");
        }

        // Simple access token extraction from Authorization header (bearer)
        String accessToken = getAccessToken(request);
        if (accessToken == null) {
            throw new InvalidDPoPHeaderException("Access token required for ath claim validation");
        }

        try {
            String hash = sha256Base64Url(accessToken);
            if (!ath.equals(hash)) {
                throw new InvalidDPoPHeaderException("ath claim does not match access token hash");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidDPoPHeaderException("Failed to compute access token hash"+e.getMessage());
        }
    }

    private String getRequestUriWithoutQuery(HttpServletRequest request) throws URISyntaxException {
        URI uri = new URI(request.getRequestURL().toString());
        // strip query and fragment
        URI noQueryUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
        return noQueryUri.toString();
    }

    private String getAccessToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.toLowerCase().startsWith("bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private String sha256Base64Url(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64URL.encode(hashed).toString();
    }
}