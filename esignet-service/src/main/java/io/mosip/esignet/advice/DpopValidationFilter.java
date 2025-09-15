package io.mosip.esignet.advice;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.*;

@Slf4j
@Component
public class DpopValidationFilter extends OncePerRequestFilter {

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("#{${mosip.esignet.dpop.header-filter.paths-to-validate}}")
    private List<String> pathsToValidate;

    @Value("${mosip.esignet.dpop.clock-skew:10}")
    private int maxClockSkewSeconds;

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;

    private static final Set<String> REQUIRED_CLAIMS = Set.of("htm", "htu", "iat", "jti");

    private static final String ALGO_SHA_256 = "SHA-256";

    private static final String DPOP_PREFIX = "DPoP ";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String CNF = "cnf";
    private static final String JKT = "jkt";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String path = request.getRequestURI();
        return !pathsToValidate.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException {
        try {
            boolean isUserinfo = request.getRequestURI().endsWith("/userinfo");
            Optional<String> dpopHeader = getDpopHeader(request);
            String authHeader = request.getHeader("Authorization");

            dpopHeader.ifPresentOrElse(
                    header -> validateDpopFlow(request, header, isUserinfo, authHeader),
                    () -> validateBearerUserinfo(authHeader)
            );
            filterChain.doFilter(request, response);
        } catch (InvalidDPoPHeaderException ex) {
            log.error("Unexpected DPoP validation error", ex);
            setAuthErrorResponse(response, ErrorConstants.INVALID_DPOP_PROOF, ex.getMessage());
        } catch (NotAuthenticatedException ex) {
            response.setHeader("WWW-Authenticate", "error=\""+ErrorConstants.INVALID_AUTH_TOKEN+"\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (Exception ex) {
            log.error("Unexpected DPoP validation error", ex);
            setAuthErrorResponse(response, ErrorConstants.UNKNOWN_ERROR, ex.getMessage());
        }
    }

    private Optional<String> getDpopHeader(HttpServletRequest request) {
        List<String> dpopHeaders = Collections.list(request.getHeaders(Constants.DPOP));
        if(dpopHeaders.isEmpty()) return Optional.empty();
        if(dpopHeaders.size() > 1) throw new InvalidDPoPHeaderException();
        return Optional.of(dpopHeaders.get(0));
    }

    private void validateDpopFlow(HttpServletRequest request, String dpopHeader, boolean isUserinfo, String authHeader) {
        SignedJWT jwt = parseAndValidateHeader(dpopHeader);
        JWK jwk = jwt.getHeader().getJWK();
        verifySignature(jwt, jwk);
        JWTClaimsSet claims = getClaims(jwt);
        verifyClaimValues(claims, request);
        replayCheck(claims);

        if (isUserinfo) {
            validateDpopUserinfo(authHeader, claims, jwk);
        }
    }

    private boolean isDpopBoundAccessToken(String accessToken) {
        String[] parts = accessToken.split("\\.");
        if (parts.length != 3) {
            log.error("Invalid token format");
            throw new EsignetException(ErrorConstants.INVALID_AUTH_TOKEN);
        }
        JsonNode payloadNode;
        try {
            String payload = new String(IdentityProviderUtil.b64Decode(parts[1]));
            payloadNode = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Invalid toke format", e);
            throw new EsignetException(ErrorConstants.INVALID_AUTH_TOKEN);
        }
        JsonNode jktNode = payloadNode.path(CNF).path(JKT);
        return !jktNode.isMissingNode();
    }

    private void validateCnfClaim(JWK jwk, String accessToken) {
        try {
            String thumbprint = jwk.computeThumbprint().toString();
            SignedJWT jwt = SignedJWT.parse(accessToken);
            Map<String, String> cnf = (Map<String, String>) jwt.getPayload().toJSONObject().get(CNF);
            String jkt = cnf.get(JKT);
            if(!thumbprint.equals(jkt)) throw new InvalidDPoPHeaderException();
        } catch (Exception e) {
            throw new InvalidDPoPHeaderException();
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

    private void verifyClaimValues(JWTClaimsSet claims, HttpServletRequest request) {
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

    private void validateAthClaim(JWTClaimsSet claims, String accessToken) {
        try {
            String ath = claims.getStringClaim("ath");
            if (ath == null) {
                log.error("Missing ath claim");
                throw new InvalidDPoPHeaderException();
            }
            String hash = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256,accessToken);
            if (!ath.equals(hash)) {
                log.error("ath claim does not match access token hash. Expected: {}, Actual: {}", hash, ath);
                throw new InvalidDPoPHeaderException();
            }
        } catch (Exception e) {
            log.error("Failed to compute access token hash: {}", e.getMessage());
            throw new InvalidDPoPHeaderException();
        }
    }

    private String getRequestUriWithoutQueryNormalized(HttpServletRequest request) throws URISyntaxException {
        URI uri = new URI(request.getRequestURL().toString());
        return new URI(
                uri.getScheme().toLowerCase(Locale.ROOT),
                uri.getAuthority().toLowerCase(Locale.ROOT),
                uri.normalize().getPath(),
                null, null
        ).toString();
    }

    public void validateBearerUserinfo(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new NotAuthenticatedException();
        }

        String accessToken = authHeader.substring(BEARER_PREFIX.length());
        if (isDpopBoundAccessToken(accessToken)) {
            throw new NotAuthenticatedException();
        }
    }

    public void validateDpopUserinfo(String authHeader, JWTClaimsSet dpopClaims, JWK jwk) {
        if (authHeader == null || !authHeader.startsWith(DPOP_PREFIX)) {
            throw new NotAuthenticatedException();
        }

        String accessToken = authHeader.substring(DPOP_PREFIX.length());
        validateAthClaim(dpopClaims, accessToken);
        validateCnfClaim(jwk, accessToken);
    }

}