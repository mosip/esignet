/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.AuthorizationService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.ConsentHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/authorization")
public class AuthorizationControllerV2 {



    private final AuthorizationService authorizationService;

    private final AuditPlugin auditWrapper;

    private final AuthorizationHelperService authorizationHelperService;

    private final ConsentHelperService consentHelperService;


    public AuthorizationControllerV2(@Qualifier("authorizationServiceV2") AuthorizationService authorizationService, AuditPlugin auditWrapper, AuthorizationHelperService authorizationHelperService, ConsentHelperService consentHelperService) {
        this.authorizationService = authorizationService;
        this.auditWrapper = auditWrapper;
        this.authorizationHelperService = authorizationHelperService;
        this.consentHelperService = consentHelperService;
    }

    @PostMapping("/v2/authenticate")
    public ResponseWrapper<AuthResponseV2> authenticateEndUser(@Valid @RequestBody RequestWrapper<AuthRequest>
                                                                       requestWrapper) throws EsignetException {
        ResponseWrapper<AuthResponseV2> responseWrapper = new ResponseWrapper<>();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            AuthResponse authResponse = authorizationService.authenticateUser(requestWrapper.getRequest());
            consentHelperService.validateConsent(requestWrapper.getRequest().getTransactionId());
            AuthResponseV2 authResponseV2 = authorizationHelperService.authResponseV2Mapper(authResponse);
            responseWrapper.setResponse(authResponseV2);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }
}
