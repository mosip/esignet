/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import io.mosip.esignet.services.AuthorizationHelperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.TokenRequest;
import io.mosip.esignet.core.dto.TokenResponse;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.spi.OAuthService;
import io.mosip.esignet.core.util.AuditHelper;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Autowired
    private OAuthService oAuthService;

    @Autowired
    private Validator validator;
    
    @Autowired
    private AuditPlugin auditWrapper;

    @Autowired
    private AuthorizationHelperService authorizationHelperService;

    @PostMapping(value = "/token", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public TokenResponse getToken(@RequestParam MultiValueMap<String,String> paramMap)
            throws EsignetException {
        TokenRequest tokenRequest = buildTokenRequest(paramMap);
        Set<ConstraintViolation<TokenRequest>> violations = validator.validate(tokenRequest);
        if(!violations.isEmpty() && violations.stream().findFirst().isPresent()) {
        	throw new InvalidRequestException(violations.stream().findFirst().get().getMessageTemplate());	//NOSONAR isPresent() check is done before accessing the value
        }
        try {
        	return oAuthService.getTokens(tokenRequest,false);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GENERATE_TOKEN, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(authorizationHelperService.getKeyHash(tokenRequest.getCode()), "codeHash", null), ex);
            throw ex;
        }               
    }

    @PostMapping(value = "/v2/token", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public TokenResponse getTokenV2(@RequestParam MultiValueMap<String,String> paramMap)
            throws EsignetException {
        TokenRequest tokenRequest = buildTokenRequest(paramMap);
        tokenRequest.setCode_verifier(paramMap.getFirst("code_verifier"));
        Set<ConstraintViolation<TokenRequest>> violations = validator.validate(tokenRequest);
        if(!violations.isEmpty() && violations.stream().findFirst().isPresent()) {
            throw new InvalidRequestException(violations.stream().findFirst().get().getMessageTemplate());	//NOSONAR isPresent() check is done before accessing the value
        }
        try {
            return oAuthService.getTokens(tokenRequest,true);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GENERATE_TOKEN, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(authorizationHelperService.getKeyHash(tokenRequest.getCode()),"codeHash", null), ex);
            throw ex;
        }
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> getAllJwks() {
        return oAuthService.getJwks();
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> getOAuthServerDiscoveryInfo() {
        return oAuthService.getOAuthServerDiscoveryInfo();
    }


    private TokenRequest buildTokenRequest(MultiValueMap<String,String> paramMap) {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setCode(paramMap.getFirst("code"));
        tokenRequest.setClient_id(paramMap.getFirst("client_id"));
        tokenRequest.setRedirect_uri(paramMap.getFirst("redirect_uri"));
        tokenRequest.setGrant_type(paramMap.getFirst("grant_type"));
        tokenRequest.setClient_assertion_type(paramMap.getFirst("client_assertion_type"));
        tokenRequest.setClient_assertion(paramMap.getFirst("client_assertion"));
        return tokenRequest;
    }
}
