/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
import io.mosip.esignet.core.exception.InvalidDpopHeaderException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    private static final String AUTH_HEADER = "Authorization";

    private static final String CNF = "cnf";
    private static final String JKT = "jkt";

    private static final String PAR_ENDPOINT = "pushed_authorization_request_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";
    private static final String USERINFO_ENDPOINT = "userinfo_endpoint";

    private enum OAUTH_ENDPOINT {
        PAR,
        TOKEN,
        USERINFO
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String path = request.getRequestURI();
        return !pathsToValidate.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException {
        try {
            OAUTH_ENDPOINT endpoint;
            if(request.getRequestURI().endsWith("/par")) endpoint = OAUTH_ENDPOINT.PAR;
            else if(request.getRequestURI().endsWith("/token")) endpoint = OAUTH_ENDPOINT.TOKEN;
            else if(request.getRequestURI().endsWith("/userinfo")) endpoint = OAUTH_ENDPOINT.USERINFO;
            else throw new RuntimeException(ErrorConstants.UNKNOWN_ERROR);

            Optional<String> dpopHeader = getDpopHeader(request);
            String authHeader = request.getHeader(AUTH_HEADER);

            dpopHeader.ifPresentOrElse(
                    header -> validateDpopFlow(request, header, endpoint, authHeader),
                    () -> {
                        if(OAUTH_ENDPOINT.USERINFO.equals(endpoint)) validateBearerUserinfo(authHeader);
                    }
            );
            filterChain.doFilter(request, response);
        } catch (InvalidDpopHeaderException ex) {
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
        if(dpopHeaders.size() > 1) throw new InvalidDpopHeaderException();
        return Optional.of(dpopHeaders.get(0));
    }

    private void validateDpopFlow(HttpServletRequest request, String dpopHeader, OAUTH_ENDPOINT endpoint, String authHeader) {
        SignedJWT jwt = parseAndValidateHeader(dpopHeader);
        JWK jwk = jwt.getHeader().getJWK();
        verifySignature(jwt, jwk);
        JWTClaimsSet claims = getClaims(jwt);
        verifyClaimValues(claims, request, endpoint);
        replayCheck(claims);

        if (OAUTH_ENDPOINT.USERINFO.equals(endpoint)) {
            validateDpopUserinfo(authHeader, claims, jwk);
        }
    }

    private boolean isDpopBoundAccessToken(String accessToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(accessToken);
            JSONObject cnf = (JSONObject) jwt.getJWTClaimsSet().getClaim(CNF);
            return cnf != null && cnf.get(JKT) != null;
        } catch (ParseException e) {
            log.error("Failed to parse accessToken: {}", accessToken);
            throw new NotAuthenticatedException();
        }
    }

    private void validateCnfClaim(JWK jwk, String accessToken) {
        try {
            String thumbprint = jwk.computeThumbprint().toString();
            SignedJWT jwt = SignedJWT.parse(accessToken);
            JSONObject cnf = (JSONObject) jwt.getJWTClaimsSet().getClaim(CNF);
            String jkt = cnf.getAsString(JKT);
            if(!thumbprint.equals(jkt)) throw new InvalidDpopHeaderException();
        } catch (Exception e) {
            log.error("cnf claim validation failed");
            throw new InvalidDpopHeaderException();
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
                throw new InvalidDpopHeaderException();
            }

            List<String> supportedClaims = (List<String>) discoveryMap.get("dpop_signing_alg_values_supported");

            String alg = header.getAlgorithm() != null ? header.getAlgorithm().getName() : null;
            if (alg == null || !supportedClaims.contains(alg)) {
                log.error("Invalid or unsupported alg header");
                throw new InvalidDpopHeaderException();
            }

            JWK jwk = header.getJWK();
            if (jwk == null || jwk.isPrivate()) {
                log.error("Invalid jwk header");
                throw new InvalidDpopHeaderException();
            }
            return jwt;
        } catch (ParseException e) {
            log.error("Failed to parse DPoP JWT: ");
            throw new InvalidDpopHeaderException();
        }
    }

    private void verifySignature(SignedJWT jwt, JWK jwk) {
        try {
            JWSVerifier verifier = createVerifier(jwk);
            if (!jwt.verify(verifier)) {
                log.error("DPoP JWT signature verification failed");
                throw new InvalidDpopHeaderException();
            }
        } catch (JOSEException e) {
            log.error("DPoP signature verification error: {}", e.getMessage());
            throw new InvalidDpopHeaderException();
        }
    }

    private JWSVerifier createVerifier(JWK jwk) throws InvalidDpopHeaderException, JOSEException {
        switch (jwk.getKeyType().getValue()) {
            case "RSA":
                return new RSASSAVerifier(((RSAKey) jwk).toRSAPublicKey());
            case "EC":
                return new ECDSAVerifier((ECKey) jwk);
            default:
                log.error("Unsupported JWK key type: {}", jwk.getKeyType());
                throw new InvalidDpopHeaderException();
        }
    }

    private JWTClaimsSet getClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            log.error("Failed to get JWT claims: ");
            throw new InvalidDpopHeaderException();
        }
    }

    private void verifyClaimValues(JWTClaimsSet claims, HttpServletRequest request, OAUTH_ENDPOINT endpoint) {
        try {
            String reqUri;
            switch (endpoint) {
                case PAR:
                    reqUri = discoveryMap.get(PAR_ENDPOINT).toString();
                    break;
                case TOKEN:
                    reqUri = discoveryMap.get(TOKEN_ENDPOINT).toString();
                    break;
                default:
                    reqUri = discoveryMap.get(USERINFO_ENDPOINT).toString();
                    break;
            }

            DefaultJWTClaimsVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .claim("htm", request.getMethod())
                    .claim("htu", reqUri)
                    .build(), REQUIRED_CLAIMS);
            claimsSetVerifier.setMaxClockSkew(maxClockSkewSeconds);
            claimsSetVerifier.verify(claims);
        } catch (BadJWTException e) {
            log.error("Invalid request URI: {}", e.getMessage());
            throw new InvalidDpopHeaderException();
        }

    }

    private void replayCheck(JWTClaimsSet claims) {
        String jti = claims.getJWTID();
        if (jti == null || jti.isEmpty()) {
            log.error("Missing jti claim");
            throw new InvalidDpopHeaderException();
        }
        if (cacheUtilService.checkAndMarkJti(jti)) {
            log.error("Replay detected for jti: {}", jti);
            throw new InvalidDpopHeaderException();
        }
    }

    private void validateAthClaim(JWTClaimsSet claims, String accessToken) {
        try {
            String ath = claims.getStringClaim("ath");
            if (ath == null) {
                log.error("Missing ath claim");
                throw new InvalidDpopHeaderException();
            }
            String hash = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256,accessToken);
            if (!ath.equals(hash)) {
                log.error("ath claim does not match access token hash. Expected: {}, Actual: {}", hash, ath);
                throw new InvalidDpopHeaderException();
            }
        } catch (Exception e) {
            log.error("Failed to compute access token hash: {}", e.getMessage());
            throw new InvalidDpopHeaderException();
        }
    }

    public void validateBearerUserinfo(String authHeader) {
        if(authHeader == null) throw new NotAuthenticatedException();
        String[] parts = authHeader.split(" ");
        if(parts.length != 2) throw new NotAuthenticatedException();
        String accessToken = parts[1];
        if (isDpopBoundAccessToken(accessToken)) {
            throw new NotAuthenticatedException();
        }
        if (!authHeader.startsWith(BEARER_PREFIX)) {
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