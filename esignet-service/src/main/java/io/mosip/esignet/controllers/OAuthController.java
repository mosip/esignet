/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import java.util.Map;
import jakarta.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.services.AuthorizationHelperService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.exception.EsignetException;
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
    public TokenResponse getToken(@Validated @ModelAttribute TokenRequest tokenRequest) {
        try {
            TokenRequestV2 tokenRequestV2 = new TokenRequestV2();
            BeanUtils.copyProperties(tokenRequest, tokenRequestV2);  // tokenRequestV2.setCodeVerifier(null);
            return oAuthService.getTokens(tokenRequestV2, false);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GENERATE_TOKEN, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(authorizationHelperService.getKeyHash(tokenRequest.getCode()), "codeHash", null), ex);
            throw ex;
        }               
    }

    @PostMapping(value = "/v2/token", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public TokenResponse getTokenV2(@Validated @ModelAttribute TokenRequestV2 tokenRequest) {
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
    @ResponseStatus(HttpStatus.CREATED)
    public PushedAuthorizationResponse authorize(@Validated @ModelAttribute PushedAuthorizationRequest pushedAuthorizationRequest)
            throws EsignetException {
        try {
            return oAuthService.authorize(pushedAuthorizationRequest);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.PAR_REQUEST, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(pushedAuthorizationRequest.getClient_id(), "clientId", null), ex);
            throw ex;
        }
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

}
