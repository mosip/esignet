package io.mosip.esignet.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.SecurityHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class DpopValidationFilter extends OncePerRequestFilter {

    private static final String DPOP_HEADER = "DPoP";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DPOP_PREFIX = "DPoP ";
    private static final String USERINFO_ENDPOINT = "/oidc/userinfo";
    private static final String CNF = "cnf";
    private static final String JKT = "jkt";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageSource messageSource;

    @Autowired

    private SecurityHelperService securityHelperService;

    @Value("#{${mosip.esignet.dpop.validation.urls}}")
    private List<String> dpopValidationUrls;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        final String path = request.getRequestURI();
        return dpopValidationUrls.stream().noneMatch(path::endsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String path = request.getRequestURI();

        try {
            log.debug("Started DPoP validation for endpoint: {}", path);

            if (path.endsWith(USERINFO_ENDPOINT)) {
                validateUserinfoEndpoint(request);
            }

            filterChain.doFilter(request, response);
        } catch (EsignetException e) {
            handleDpopError(response, e);
        }
    }

    private void validateUserinfoEndpoint(HttpServletRequest request) {
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        final String accessToken = extractAccessToken(authHeader);
        final String jktFromToken = extractJktFromAccessToken(accessToken);

        if(jktFromToken != null) {
            final String dpopHeader = request.getHeader(DPOP_HEADER);
            if (!StringUtils.hasText(dpopHeader) || !isDpopThumbprintValid(dpopHeader, jktFromToken)) {
                log.error("DPoP proof is missing or does not match with the jkt in access token");
                throw new EsignetException(ErrorConstants.INVALID_DPOP_PROOF);
            }
            log.debug("Dpop validation passed for access token");
        }
    }

    private String extractAccessToken(String authHeader) {
        if(authHeader == null) {
            log.error("Missing authorization header");
            throw new EsignetException(ErrorConstants.MISSING_HEADER);
        }
        if (authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        } else if (authHeader.startsWith(DPOP_PREFIX)) {
            return authHeader.substring(DPOP_PREFIX.length());
        } else {
            log.error("Invalid Authorization scheme");
            throw new EsignetException(ErrorConstants.INVALID_AUTH_TOKEN);
        }
    }

    private String extractJktFromAccessToken(String accessToken) {
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
        if (jktNode.isMissingNode()) {
            return null;
        }
        return jktNode.asText();
    }

    private void handleDpopError(HttpServletResponse response, EsignetException ex) {
        String errorCode = ex.getErrorCode();

        if (ErrorConstants.INVALID_DPOP_PROOF.equals(errorCode)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setHeader("WWW-Authenticate", "DPoP error=\"invalid_dpop_proof\"");
        } else if(ErrorConstants.INVALID_AUTH_TOKEN.equals(errorCode)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "error=\"invalid_token\"");
        } else if(ErrorConstants.MISSING_HEADER.equals(errorCode)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "error=\"missing_header\"");
        } else {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setHeader("WWW-Authenticate", "error=\"unknown_error\"");
        }
    }

    private String getMessage(String errorCode) {
        try {
            return messageSource.getMessage(errorCode, null, Locale.getDefault());
        } catch (NoSuchMessageException ex) {
            log.error("Message not found in the i18n bundle", ex);
        }
        return errorCode;
    }

    private boolean isDpopThumbprintValid(String dpopHeader, String dpopJkt) {
        try {
            SignedJWT dpopJwt = SignedJWT.parse(dpopHeader);
            String thumbprint = securityHelperService.computeJwkThumbprint(dpopJwt.getHeader().getJWK());
            return dpopJkt.equals(thumbprint);
        } catch (Exception e) {
            log.error("invalid dpop header", e);
            return false;
        }
    }
}
