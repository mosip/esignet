package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.TokenRequest;
import io.mosip.idp.core.dto.TokenResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.OAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Autowired
    private OAuthService iOauth;

    @PostMapping("/token")
    public TokenResponse getToken(@Valid @RequestBody TokenRequest tokenRequest)
            throws IdPException {
        return iOauth.getTokens(tokenRequest);
    }
}
