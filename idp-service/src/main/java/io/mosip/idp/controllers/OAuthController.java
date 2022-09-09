/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.TokenRequest;
import io.mosip.idp.core.dto.TokenResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.OAuthService;
import org.jose4j.jwk.JsonWebKeySet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Autowired
    private OAuthService oAuthService;

    @PostMapping(value = "/token", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public TokenResponse getToken(@RequestParam MultiValueMap<String,String> paramMap)
            throws IdPException {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode(paramMap.getFirst("code"));
        tokenRequest.setClient_id(paramMap.getFirst("client_id"));
        tokenRequest.setRedirect_uri(paramMap.getFirst("redirect_uri"));
        tokenRequest.setGrant_type(paramMap.getFirst("grant_type"));
        tokenRequest.setClient_assertion_type(paramMap.getFirst("client_assertion_type"));
        tokenRequest.setClient_assertion(paramMap.getFirst("client_assertion"));
        return oAuthService.getTokens(tokenRequest);
    }

    @GetMapping("/.well-known/jwks.json")
    public JsonWebKeySet getAllJwks() {
        return oAuthService.getJwks();
    }
}
