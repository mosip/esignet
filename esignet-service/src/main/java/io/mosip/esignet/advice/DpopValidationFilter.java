package io.mosip.esignet.advice;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.InvalidDPoPHeaderException;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

        Enumeration<String> dpopHeaders = request.getHeaders("DPoP");
        List<String> dpopHeaderList = Collections.list(dpopHeaders);

        if (dpopHeaderList.size() != 1) {
            log.warn("DPoP header missing or multiple headers found. Skipping DPoP validation.");
            // skip DPoP validation for now
            filterChain.doFilter(request, response);
            return;
        }

        String dpopHeader = dpopHeaderList.get(0);

        try {
            SignedJWT jwt = parseAndValidateHeader(dpopHeader);
            JWK jwk = jwt.getHeader().getJWK();
            verifySignature(jwt, jwk);
            JWTClaimsSet claims = getClaims(jwt);
            checkRequiredClaims(claims);
            verifyClaimsBasicValues(claims, request);
            replayCheck(claims);

            if (uri.endsWith("/userinfo")) {
                checkCnfJktClaim(claims, jwk);
                validateAthClaim(claims, request);
            } else {
                checkCnfClaimPresent(claims);
            }

            filterChain.doFilter(request, response);
        } catch (InvalidDPoPHeaderException ex) {
            log.error("DPoP validation failed: {}", ex.getMessage());
            setAuthErrorHeader(response, ErrorConstants.INVALID_DPOP_PROOF, ex.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid DPoP proof: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected DPoP validation error", ex);
            setAuthErrorHeader(response, ErrorConstants.INVALID_DPOP_PROOF,ex.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid DPoP proof");
        }
    }

    private void setAuthErrorHeader(HttpServletResponse response, String error, String description) {
        String headerValue = String.format(
                "DPoP error=\"%s\", error_description=\"%s\", algs=\"ES256 PS256\"",
                error, description);
        response.setHeader("WWW-Authenticate", headerValue);
    }

    private SignedJWT parseAndValidateHeader(String dpopJwt) {
        try {
            SignedJWT jwt = SignedJWT.parse(dpopJwt);
            JWSHeader header = jwt.getHeader();

            if (header == null || header.getType() == null || !"dpop+jwt".equalsIgnoreCase(header.getType().toString())) {
                log.error("Invalid typ header: expected dpop+jwt");
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
            }

            String alg = header.getAlgorithm() != null ? header.getAlgorithm().getName() : null;
            if (alg == null || "none".equalsIgnoreCase(alg) || !SUPPORTED_ALGORITHMS.contains(alg)) {
                log.error("Invalid or unsupported alg header");
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
            }

            JWK jwk = header.getJWK();
            if (jwk == null || jwk.isPrivate()) {
                log.error("Invalid jwk header");
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
            }
            return jwt;
        } catch (ParseException e) {
            throw new InvalidDPoPHeaderException("Failed to parse DPoP JWT: " + e.getMessage());
        }
    }

    private void verifySignature(SignedJWT jwt, JWK jwk) {
        try {
            JWSVerifier verifier = createVerifier(jwk);
            if (!jwt.verify(verifier)) {
                log.error("DPoP JWT signature verification failed");
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
            }
        } catch (JOSEException e) {
            log.error("DPoP signature verification error: {}", e.getMessage());
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
    }

    private JWSVerifier createVerifier(JWK jwk) throws InvalidDPoPHeaderException, JOSEException {
        switch (jwk.getKeyType().getValue()) {
            case "RSA":
                return new RSASSAVerifier(((RSAKey) jwk).toRSAPublicKey());
            case "EC":
                return new ECDSAVerifier((ECKey) jwk);
            default:
                log.error("Unsupported JWK key type: {}", jwk.getKeyType());
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
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
                log.error("Missing required claim: {}", required);
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
            }
        }
    }

    private void verifyClaimsBasicValues(JWTClaimsSet claims, HttpServletRequest request) throws ParseException {
        String htm = claims.getStringClaim("htm");
        if (!request.getMethod().equalsIgnoreCase(htm)) {
            log.error("htm claim does not match HTTP method. Expected: {}, Actual: {}", request.getMethod(), htm);
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        String htu = claims.getStringClaim("htu");
        String reqUri;
        try {
            reqUri = getRequestUriWithoutQueryNormalized(request);
        } catch (URISyntaxException e) {
            log.error("Invalid request URI: {}", e.getMessage());
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        if (!htu.equals(reqUri)) {
            log.error("htu claim does not match request URI. Expected: {}, Actual: {}", reqUri, htu);
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        Date iatDate = claims.getIssueTime();
        if (iatDate == null) {
            log.error("Missing iat claim");
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        Instant iat = iatDate.toInstant();
        Instant now = Instant.now();
        if (Math.abs(now.getEpochSecond() - iat.getEpochSecond()) > MAX_CLOCK_SKEW_SECONDS) {
            log.error("iat claim is outside acceptable window. iat: {}, now: {}", iat, now);
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
    }

    private void replayCheck(JWTClaimsSet claims) {
        String jti = claims.getJWTID();
        if (jti == null || jti.isEmpty()) {
            log.error("Missing jti claim");
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        if (cacheUtilService.checkAndMarkJti(jti)) {
            log.error("Replay detected for jti: {}", jti);
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
    }

    private void checkCnfClaimPresent(JWTClaimsSet claims) {
        Object cnf = claims.getClaim("cnf");
        if (cnf == null) {
            log.error("Missing cnf claim");
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
    }

    private void checkCnfJktClaim(JWTClaimsSet claims, JWK proofJwk) {
        Object obj = claims.getClaim("cnf");
        if (!(obj instanceof Map)) {
            log.error("Invalid cnf claim: expected Map but got {}", obj == null ? "null" : obj.getClass().getName());
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cnfMap = (Map<String, Object>) obj;
        if (!cnfMap.containsKey("jkt")) {
            log.error("cnf claim missing 'jkt' key");
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        String jktVal = cnfMap.get("jkt").toString();
        try {
            String actualThumbprint = proofJwk.computeThumbprint().toString();
            if (!jktVal.equals(actualThumbprint)) {
                log.error("cnf jkt does not match proof key thumbprint. Expected: {}, Actual: {}", actualThumbprint, jktVal);
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
            }
        } catch (JOSEException e) {
            log.error("Failed to compute proof key thumbprint: {}", e.getMessage());
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
    }

    private void validateAthClaim(JWTClaimsSet claims, HttpServletRequest request) throws ParseException {
        String ath = claims.getStringClaim("ath");
        if (ath == null || ath.isEmpty()) {
            log.error("Missing ath claim");
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        String accessToken = getAccessToken(request);
        if (accessToken == null) {
            log.error("Access token required for ath claim validation but not found");
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
        try {
            String hash = sha256Base64Url(accessToken);
            if (!ath.equals(hash)) {
                log.error("ath claim does not match access token hash. Expected: {}, Actual: {}", hash, ath);
                throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
            }
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to compute access token hash: {}", e.getMessage());
            throw new InvalidDPoPHeaderException(ErrorConstants.INVALID_DPOP_HEADER);
        }
    }

    private String getRequestUriWithoutQueryNormalized(HttpServletRequest request) throws URISyntaxException {
        URI uri = new URI(request.getRequestURL().toString());
        URI noQueryUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
        String normalized = noQueryUri.normalize().toString();
        URI normalizedUri = new URI(normalized);
        return new URI(
                normalizedUri.getScheme().toLowerCase(),
                normalizedUri.getAuthority().toLowerCase(),
                normalizedUri.getPath(),
                null,
                null).toString();
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