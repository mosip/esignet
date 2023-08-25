package io.mosip.esignet.vci.filter;

import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class AccessTokenValidationFilter implements Filter {

    @Value("${mosip.esignet.vci.authn.issuer-uri}")
    private String issuerUri;

    @Value("${mosip.esignet.vci.authn.jwk-set-uri}")
    private String jwkSetUri;

    @Value("#{${mosip.esignet.vci.authn.allowed-audiences}}")
    private List<String> allowedAudiences;

    @Autowired
    private ParsedAccessToken parsedAccessToken;

    private NimbusJwtDecoder nimbusJwtDecoder;


    private boolean isJwt(String token) {
        return token.split("\\.").length == 3;
    }

    private NimbusJwtDecoder getNimbusJwtDecoder() {
        if(nimbusJwtDecoder == null) {
            nimbusJwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            nimbusJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    new JwtIssuerValidator(issuerUri),
                    new JwtClaimValidator<>(JwtClaimNames.AUD, allowedAudiences::containsAll),
                    new JwtClaimValidator<>(JwtClaimNames.SUB, Objects::nonNull),
                    new JwtClaimValidator<>(JwtClaimNames.IAT, Objects::nonNull),
                    new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull)));
        }
        return nimbusJwtDecoder;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest)request;
        String authorizationHeader = httpServletRequest.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            //validate access token no matter if its JWT or Opaque
            if(isJwt(token)) {
                try {
                    //Verifies signature and claim predicates, If invalid throws exception
                    Jwt jwt = getNimbusJwtDecoder().decode(token);
                    parsedAccessToken.setClaims(jwt.getClaims());
                    parsedAccessToken.setActive(true);
                    chain.doFilter(request, response);
                    return;

                } catch (Exception e) {
                    log.error("Access token validation failed", e);
                    throw new NotAuthenticatedException();
                }
            }
        }

        log.error("No Bearer / Opaque token provided, continue with the request chain");
        parsedAccessToken.setActive(false);
        chain.doFilter(request, response);
    }
}
