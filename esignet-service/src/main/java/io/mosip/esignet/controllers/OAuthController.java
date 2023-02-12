/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.OAuthError;
import io.mosip.esignet.core.dto.TokenRequest;
import io.mosip.esignet.core.dto.TokenResponse;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.spi.OAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Autowired
    private OAuthService oAuthService;

    @Autowired
    private Validator validator;

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
        Set<ConstraintViolation<TokenRequest>> violations = validator.validate(tokenRequest);
        if(!violations.isEmpty()) {
            throw new InvalidRequestException(violations.stream().findFirst().get().getMessageTemplate());
        }
        return oAuthService.getTokens(tokenRequest);
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> getAllJwks() {
        return oAuthService.getJwks();
    }
}
