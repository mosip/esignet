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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_TRANSACTION;

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
                                                                         requestWrapper, HttpServletRequest httpServletRequest) throws EsignetException {
        ResponseWrapper<AuthResponseV2> responseWrapper = new ResponseWrapper<>();
        try {
            AuthResponseV2 authResponse = authorizationService.authenticateUserV3(requestWrapper.getRequest(), httpServletRequest);
            responseWrapper.setResponse(authResponse);
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/v3/oauth-details")
    public ResponseWrapper<OAuthDetailResponseV2> getOauthDetailsV3(@Valid @RequestBody RequestWrapper<OAuthDetailRequestV3>
                                                                            requestWrapper, HttpServletRequest httpServletRequest) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.getOauthDetailsV3(requestWrapper.getRequest(),httpServletRequest));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GET_OAUTH_DETAILS, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getClientId()), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/prepare-signup-redirect")
    public ResponseWrapper<SignupRedirectResponse> prepareSignupRedirect(@Valid @RequestBody RequestWrapper<SignupRedirectRequest> requestWrapper,
                                                                         HttpServletResponse response) {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.prepareSignupRedirect(requestWrapper.getRequest(), response));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.PREPARE_SIGNUP_REDIRECT, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId()), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @GetMapping("/claim-details")
    public ResponseWrapper<ClaimDetailResponse> getClaimDetails(@Valid @NotBlank(message = INVALID_TRANSACTION)
                                                                    @RequestHeader("oauth-details-key") String transactionId) {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.getClaimDetails(transactionId));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.CONSENT_DETAILS, ActionStatus.ERROR, AuditHelper.buildAuditDto(transactionId), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/resume")
    public ResponseWrapper<ResumeResponse> resumeHaltedTransaction(@Valid @RequestBody RequestWrapper<ResumeRequest> requestWrapper) {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        try {
            responseWrapper.setResponse(authorizationService.resumeHaltedTransaction(requestWrapper.getRequest()));
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.CONSENT_DETAILS, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId()), ex);
            throw ex;
        }
        return responseWrapper;
    }
}
