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
import io.mosip.esignet.services.ConsentHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/linked-authorization/v2")
public class LinkedAuthorizationControllerV2 {

    @Autowired
    private LinkedAuthorizationService linkedAuthorizationService;
    @Autowired
    private AuditPlugin auditWrapper;
    @Autowired
    private ConsentHelperService consentHelperService;


    @PostMapping("/authenticate")
    public ResponseWrapper<LinkedKycAuthResponseV2> authenticate(@Valid @RequestBody RequestWrapper<LinkedKycAuthRequest>
                                                                         requestWrapper) throws EsignetException {
        ResponseWrapper<LinkedKycAuthResponseV2> responseWrapper = new ResponseWrapper<>();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            LinkedKycAuthResponse linkedKycAuthResponse = linkedAuthorizationService.authenticateUser(requestWrapper.getRequest());
            LinkedKycAuthResponseV2 linkedKycAuthResponseV2  = consentHelperService.processLinkedConsent(requestWrapper.getRequest().getLinkedTransactionId());
            responseWrapper.setResponse(linkedKycAuthResponseV2);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_AUTHENTICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkedTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }
    @PostMapping("/consent")
    public ResponseWrapper<LinkedConsentResponse> saveConsent(@Valid @RequestBody RequestWrapper<LinkedConsentRequestV2>
                                                                      requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            LinkedConsentRequestV2 linkedConsentRequestV2 = requestWrapper.getRequest();
            LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
            linkedConsentRequest.setLinkedTransactionId(linkedConsentRequestV2.getLinkedTransactionId());
            linkedConsentRequest.setAcceptedClaims(linkedConsentRequestV2.getAcceptedClaims());
            linkedConsentRequest.setPermittedAuthorizeScopes(linkedConsentRequestV2.getPermittedAuthorizeScopes());
            LinkedConsentResponse linkedConsentResponse = linkedAuthorizationService.saveConsent(linkedConsentRequest);
            consentHelperService.addUserConsent(linkedConsentRequest.getLinkedTransactionId(), true, linkedConsentRequestV2.getSignature());
            responseWrapper.setResponse(linkedConsentResponse);
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.SAVE_CONSENT, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkedTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }
}
