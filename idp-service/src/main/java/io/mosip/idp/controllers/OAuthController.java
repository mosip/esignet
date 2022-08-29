package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.TokenRequest;
import io.mosip.idp.core.dto.TokenResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.OAuthService;
import org.jose4j.jwk.JsonWebKeySet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Autowired
    private OAuthService oAuthService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse getToken(@Valid @RequestBody TokenRequest tokenRequest)
            throws IdPException {
        return oAuthService.getTokens(tokenRequest);
    }

    @GetMapping("/.well-known/jwks.json")
    public JsonWebKeySet getAllJwks() {
        return oAuthService.getJwks();
    }
}
