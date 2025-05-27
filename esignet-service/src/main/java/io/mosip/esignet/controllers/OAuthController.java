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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    @PostMapping(value = "/par", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper<ParResponse>> pushedAuthorizationRequest(@RequestParam MultiValueMap<String, String> paramMap)
            throws EsignetException {
        if (paramMap.containsKey("request_uri")) {
            throw new EsignetException(ErrorConstants.INVALID_REQUEST);
        }

        ParRequest parRequest = buildParRequest(paramMap);
        Set<ConstraintViolation<ParRequest>> violations = validator.validate(parRequest);
        if(!violations.isEmpty() && violations.stream().findFirst().isPresent()) {
            throw new InvalidRequestException(violations.stream().findFirst().get().getMessageTemplate());	//NOSONAR isPresent() check is done before accessing the value
        }
        try {
            ParResponse response = oAuthService.handleParRequest(parRequest);
            ResponseWrapper<ParResponse> responseWrapper=new ResponseWrapper<>();
            responseWrapper.setResponse(response);
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseWrapper);
        } catch (InvalidRequestException e) {
            return ResponseEntity.badRequest().body((ResponseWrapper<ParResponse>) Map.of("error", "invalid_request", "error_description", e.getMessage()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.PAR_REQUEST, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(parRequest.getClientId(), "clientId", null), ex);
            throw ex;
        }
    }

    private ParRequest buildParRequest(MultiValueMap<String, String> paramMap) {
        ParRequest req = new ParRequest();
        req.setScope(paramMap.getFirst("scope"));
        req.setResponseType(paramMap.getFirst("response_type"));
        req.setClientId(paramMap.getFirst("client_id"));
        req.setRedirectUri(paramMap.getFirst("redirect_uri"));
        req.setState(paramMap.getFirst("state"));
        req.setNonce(paramMap.getFirst("nonce"));
        req.setDisplay(paramMap.getFirst("display"));
        req.setPrompt(paramMap.getFirst("prompt"));
        req.setAcrValues(paramMap.getFirst("acr_values"));
        String claimsJson = paramMap.getFirst("claims");
        if (claimsJson != null) {
            try {
                ClaimsV2 claims = objectMapper.readValue(claimsJson, ClaimsV2.class);
                req.setClaims(claims);
            } catch (JsonProcessingException e) {
                throw new InvalidRequestException("Invalid JSON format for claims parameter");
            }
        }
        req.setMaxAge(paramMap.getFirst("max_age") != null ? Integer.parseInt(paramMap.getFirst("max_age")) : null);
        req.setClaimsLocales(paramMap.getFirst("claims_locales"));
        req.setUiLocales(paramMap.getFirst("ui_locales"));
        req.setCodeChallenge(paramMap.getFirst("code_challenge"));
        req.setCodeChallengeMethod(paramMap.getFirst("code_challenge_method"));
        req.setIdTokenHint(paramMap.getFirst("id_token_hint"));
        req.setClientAssertionType(paramMap.getFirst("client_assertion_type"));
        req.setClientAssertion(paramMap.getFirst("client_assertion"));
        return req;
    }

    @GetMapping("/.well-known/jwks.json")
    @CrossOrigin(origins = "*")
    public Map<String, Object> getAllJwks() {
        return oAuthService.getJwks();
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    @CrossOrigin(origins = "*")
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
