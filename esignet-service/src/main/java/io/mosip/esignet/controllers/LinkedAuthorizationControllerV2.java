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
import io.mosip.esignet.core.spi.LinkedAuthorizationService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/linked-authorization")
public class LinkedAuthorizationControllerV2 {

    private final LinkedAuthorizationService linkedAuthorizationService;
    private final AuditPlugin auditWrapper;
    private final AuthorizationHelperService authorizationHelperService;

    public LinkedAuthorizationControllerV2(@Qualifier("linkedAuthorizationServiceV2") LinkedAuthorizationService linkedAuthorizationService, AuditPlugin auditWrapper, AuthorizationHelperService authorizationHelperService) {
        this.linkedAuthorizationService = linkedAuthorizationService;
        this.auditWrapper = auditWrapper;
        this.authorizationHelperService = authorizationHelperService;
    }


    @PostMapping("/v2/authenticate")
    public ResponseWrapper<LinkedKycAuthResponseV2> authenticate(@Valid @RequestBody RequestWrapper<LinkedKycAuthRequest>
                                                                            requestWrapper) throws EsignetException {
        ResponseWrapper<LinkedKycAuthResponseV2> responseWrapper = new ResponseWrapper<>();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            LinkedKycAuthResponse linkedKycAuthResponse = linkedAuthorizationService.authenticateUser(requestWrapper.getRequest());
            LinkedKycAuthResponseV2 linkedKycAuthResponseV2 = authorizationHelperService.linkedKycAuthResponseV2Mapper(linkedKycAuthResponse);
        	responseWrapper.setResponse(linkedKycAuthResponseV2);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_AUTHENTICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkedTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }



}
