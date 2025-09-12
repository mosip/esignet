package io.mosip.esignet.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidDPoPHeaderException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
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
import java.text.ParseException;
import java.time.Instant;
import java.util.*;

@Slf4j
@ControllerAdvice
public class DpopValidationFilter extends OncePerRequestFilter {

    @Autowired
    private CacheUtilService cacheUtilService;

    @Value("#{${mosip.esignet.dpop.header-filter.paths-to-validate}}")
    private List<String> pathsToValidate;

    @Value("${mosip.esignet.dpop.clock-skew:10}")
    private int maxClockSkewSeconds;

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;

    private static final Set<String> REQUIRED_CLAIMS = Set.of("htm", "htu", "iat", "jti");

    public static final String ALGO_SHA_256 = "SHA-256";

    public static final String DPOP_PREFIX = "DPoP ";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String path = request.getRequestURI();
        return !pathsToValidate.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        Enumeration<String> dpopHeaders = request.getHeaders(Constants.DPOP);
        List<String> dpopHeaderList = Collections.list(dpopHeaders);

        if (dpopHeaderList.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (dpopHeaderList.size()>1) {
            log.warn("Multiple DPoP headers found");
            throw new InvalidDPoPHeaderException();
        }

        String dpopHeader = dpopHeaderList.get(0);

        try {
            SignedJWT jwt = parseAndValidateHeader(dpopHeader);
            JWK jwk = jwt.getHeader().getJWK();
            verifySignature(jwt, jwk);
            JWTClaimsSet claims = getClaims(jwt);
            verifyClaimValues(claims, request);
            replayCheck(claims);

            if (uri.endsWith("/userinfo")) {
                validateAthClaim(claims, request);
            }

            filterChain.doFilter(request, response);
        } catch (InvalidDPoPHeaderException | ParseException ex) {
            log.error("Unexpected DPoP validation error", ex);
            setAuthErrorResponse(response, ErrorConstants.INVALID_DPOP_PROOF, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected DPoP validation error", ex);
            setAuthErrorResponse(response, ErrorConstants.UNKNOWN_ERROR, ex.getMessage());
        }
    }

    private void setAuthErrorResponse(HttpServletResponse response, String error, String description) throws IOException {
        String headerValue = String.format(
                "DPoP error=\"%s\", error_description=\"%s\", algs=\"ES256 PS256\"",
                error, description
        );
        response.setHeader("WWW-Authenticate", headerValue);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode errorNode = mapper.createObjectNode();
        errorNode.put("error", error);
        errorNode.put("error_description", description);
        response.getWriter().write(mapper.writeValueAsString(errorNode));
    }

    private SignedJWT parseAndValidateHeader(String dpopJwt) {
        try {
            SignedJWT jwt = SignedJWT.parse(dpopJwt);
            JWSHeader header = jwt.getHeader();

            if (header == null || header.getType() == null || !"dpop+jwt".equals(header.getType().toString())) {
                log.error("Invalid typ header: expected dpop+jwt");
                throw new InvalidDPoPHeaderException();
            }

            List<String> supportedClaims = (List<String>) discoveryMap.get("dpop_signing_alg_values_supported");

            String alg = header.getAlgorithm() != null ? header.getAlgorithm().getName() : null;
            if (alg == null || !supportedClaims.contains(alg)) {
                log.error("Invalid or unsupported alg header");
                throw new InvalidDPoPHeaderException();
            }

            JWK jwk = header.getJWK();
            if (jwk == null || jwk.isPrivate()) {
                log.error("Invalid jwk header");
                throw new InvalidDPoPHeaderException();
            }
            return jwt;
        } catch (ParseException e) {
            log.error("Failed to parse DPoP JWT: ");
            throw new InvalidDPoPHeaderException();
        }
    }

    private void verifySignature(SignedJWT jwt, JWK jwk) {
        try {
            JWSVerifier verifier = createVerifier(jwk);
            if (!jwt.verify(verifier)) {
                log.error("DPoP JWT signature verification failed");
                throw new InvalidDPoPHeaderException();
            }
        } catch (JOSEException e) {
            log.error("DPoP signature verification error: {}", e.getMessage());
            throw new InvalidDPoPHeaderException();
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
                throw new InvalidDPoPHeaderException();
        }
    }

    private JWTClaimsSet getClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            log.error("Failed to get JWT claims: ");
            throw new InvalidDPoPHeaderException();
        }
    }

    private void verifyClaimValues(JWTClaimsSet claims, HttpServletRequest request) throws ParseException {
        try {
            String reqUri = getRequestUriWithoutQueryNormalized(request);

            DefaultJWTClaimsVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .claim("htm", request.getMethod())
                    .claim("htu", reqUri)
                    .build(), REQUIRED_CLAIMS);
            claimsSetVerifier.setMaxClockSkew(maxClockSkewSeconds);
            claimsSetVerifier.verify(claims);
        }catch (URISyntaxException | BadJWTException e) {
            log.error("Invalid request URI: {}", e.getMessage());
            throw new InvalidDPoPHeaderException();
        }

    }

    private void replayCheck(JWTClaimsSet claims) {
        String jti = claims.getJWTID();
        if (jti == null || jti.isEmpty()) {
            log.error("Missing jti claim");
            throw new InvalidDPoPHeaderException();
        }
        if (cacheUtilService.checkAndMarkJti(jti)) {
            log.error("Replay detected for jti: {}", jti);
            throw new InvalidDPoPHeaderException();
        }
    }

    private void checkCnfClaimPresent(JWTClaimsSet claims) {
        Object cnf = claims.getClaim("cnf");
        if (cnf == null) {
            log.error("Missing cnf claim");
            throw new InvalidDPoPHeaderException();
        }
    }

    private void checkCnfJktClaim(JWTClaimsSet claims, JWK proofJwk) {
        Object obj = claims.getClaim("cnf");
        if (!(obj instanceof Map)) {
            log.error("Invalid cnf claim: expected Map but got {}", obj == null ? "null" : obj.getClass().getName());
            throw new InvalidDPoPHeaderException();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cnfMap = (Map<String, Object>) obj;
        if (!cnfMap.containsKey("jkt")) {
            log.error("cnf claim missing 'jkt' key");
            throw new InvalidDPoPHeaderException();
        }
        String jktVal = cnfMap.get("jkt").toString();
        try {
            String actualThumbprint = proofJwk.computeThumbprint().toString();
            if (!jktVal.equals(actualThumbprint)) {
                log.error("cnf jkt does not match proof key thumbprint. Expected: {}, Actual: {}", actualThumbprint, jktVal);
                throw new InvalidDPoPHeaderException();
            }
        } catch (JOSEException e) {
            log.error("Failed to compute proof key thumbprint: {}", e.getMessage());
            throw new InvalidDPoPHeaderException();
        }
    }

    private void validateAthClaim(JWTClaimsSet claims, HttpServletRequest request) throws ParseException {

        String ath = claims.getStringClaim("ath");
        if (ath == null || ath.isEmpty()) {
            log.error("Missing ath claim");
            throw new InvalidDPoPHeaderException();
        }
        String accessToken = getAccessToken(request);
        try {
            String hash = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256,accessToken);
            if (!ath.equals(hash)) {
                log.error("ath claim does not match access token hash. Expected: {}, Actual: {}", hash, ath);
                throw new InvalidDPoPHeaderException();
            }
        } catch (EsignetException e) {
            log.error("Failed to compute access token hash: {}", e.getMessage());
            throw new InvalidDPoPHeaderException();
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
        if (auth != null && auth.startsWith(DPOP_PREFIX)) {
            return auth.substring(DPOP_PREFIX.length());
        }
        throw new InvalidDPoPHeaderException();
    }

}