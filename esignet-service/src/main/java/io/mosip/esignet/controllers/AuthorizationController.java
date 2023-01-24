/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.spi.AuditWrapper;
import io.mosip.esignet.core.spi.AuthorizationService;
import io.mosip.esignet.core.constants.Action;
import io.mosip.esignet.core.constants.ActionStatus;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/authorization")
public class AuthorizationController {

    @Autowired
    AuthorizationService authorizationService;

    @Autowired
    AuditWrapper auditWrapper;

    @PostMapping("/oauth-details")
    public ResponseWrapper<OAuthDetailResponse> getOauthDetails(@Valid @RequestBody RequestWrapper<OAuthDetailRequest>
                                                                            requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            responseWrapper.setResponse(authorizationService.getOauthDetails(requestWrapper.getRequest()));
        } catch (IdPException ex) {
            auditWrapper.logAudit(Action.GET_OAUTH_DETAILS, ActionStatus.ERROR, new AuditDTO(requestWrapper.getRequest().getClientId()), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/send-otp")
    public ResponseWrapper<OtpResponse> sendOtp(@Valid @RequestBody RequestWrapper<OtpRequest> requestWrapper)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            responseWrapper.setResponse(authorizationService.sendOtp(requestWrapper.getRequest()));
        } catch (IdPException ex) {
            auditWrapper.logAudit(Action.SEND_OTP, ActionStatus.ERROR, new AuditDTO(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/authenticate")
    public ResponseWrapper<AuthResponse> authenticateEndUser(@Valid @RequestBody RequestWrapper<AuthRequest>
                                                                        requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            responseWrapper.setResponse(authorizationService.authenticateUser(requestWrapper.getRequest()));
        } catch (IdPException ex) {
            auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.ERROR, new AuditDTO(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/auth-code")
    public ResponseWrapper<AuthCodeResponse> getAuthorizationCode(@Valid @RequestBody RequestWrapper<AuthCodeRequest>
                                                                              requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            responseWrapper.setResponse(authorizationService.getAuthCode(requestWrapper.getRequest()));
        } catch (IdPException ex) {
            auditWrapper.logAudit(Action.GET_AUTH_CODE, ActionStatus.ERROR, new AuditDTO(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }
}
