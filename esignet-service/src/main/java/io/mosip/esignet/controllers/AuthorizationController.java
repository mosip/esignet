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
    AuditPlugin auditWrapper;

    /**
     * @deprecated
     * This method is no longer acceptable to get oauth detail response
     * <p> Use {@link AuthorizationController#getOauthDetailsV2(RequestWrapper<OAuthDetailRequest>)} </p>
     *
     * @param requestWrapper
     * @return
     * @throws EsignetException
     */
    @Deprecated()
    @PostMapping("/oauth-details")
    public ResponseWrapper<OAuthDetailResponse> getOauthDetails(@Valid @RequestBody RequestWrapper<OAuthDetailRequest>
                                                                            requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.getOauthDetails(requestWrapper.getRequest()));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GET_OAUTH_DETAILS, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getClientId()), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/v2/oauth-details")
    public ResponseWrapper<OAuthDetailResponseV2> getOauthDetailsV2(@Valid @RequestBody RequestWrapper<OAuthDetailRequestV2>
                                                                        requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.getOauthDetailsV2(requestWrapper.getRequest()));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GET_OAUTH_DETAILS, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getClientId()), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/send-otp")
    public ResponseWrapper<OtpResponse> sendOtp(@Valid @RequestBody RequestWrapper<OtpRequest> requestWrapper)
            throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.sendOtp(requestWrapper.getRequest()));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.SEND_OTP, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/authenticate")
    public ResponseWrapper<AuthResponse> authenticateEndUser(@Valid @RequestBody RequestWrapper<AuthRequest>
                                                                        requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.authenticateUser(requestWrapper.getRequest()));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/auth-code")
    public ResponseWrapper<AuthCodeResponse> getAuthorizationCode(@Valid @RequestBody RequestWrapper<AuthCodeRequest>
                                                                              requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.getAuthCode(requestWrapper.getRequest()));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GET_AUTH_CODE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/v2/authenticate")
    public ResponseWrapper<AuthResponseV2> authenticateEndUserV2(@Valid @RequestBody RequestWrapper<AuthRequest>
                                                                       requestWrapper) throws EsignetException {
        ResponseWrapper<AuthResponseV2> responseWrapper = new ResponseWrapper<>();
        try {
            AuthResponseV2 authResponse = authorizationService.authenticateUserV2(requestWrapper.getRequest());
            responseWrapper.setResponse(authResponse);
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/v3/authenticate")
    public ResponseWrapper<AuthResponseV2> authenticateEndUserV3(@Valid @RequestBody RequestWrapper<AuthRequestV2>
                                                                         requestWrapper) throws EsignetException {
        ResponseWrapper<AuthResponseV2> responseWrapper = new ResponseWrapper<>();
        try {
            AuthResponseV2 authResponse = authorizationService.authenticateUserV3(requestWrapper.getRequest());
            responseWrapper.setResponse(authResponse);
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }
}
