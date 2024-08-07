/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.LinkedAuthorizationService;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

@Slf4j
@RestController
@RequestMapping("/linked-authorization")
public class LinkedAuthorizationController {

    @Autowired
    private LinkedAuthorizationService linkedAuthorizationService;

    @Autowired
    MessageSource messageSource;
    
    @Autowired
    private AuditPlugin auditWrapper;

    @Value("${mosip.esignet.link-status-deferred-response-timeout-secs:25}")
    private long linkStatusDeferredResponseTimeout;

    @Value("${mosip.esignet.link-auth-code-deferred-response-timeout-secs:25}")
    private long linkAuthCodeDeferredResponseTimeout;

    @PostMapping("/link-code")
    public ResponseWrapper<LinkCodeResponse> generateLinkCode(@Valid @RequestBody RequestWrapper<LinkCodeRequest>
                                                                      requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
        	responseWrapper.setResponse(linkedAuthorizationService.generateLinkCode(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_CODE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return responseWrapper;
    }
    /**
     * @deprecated
     * This method is no longer acceptable to link transaction
     * <p> Use {@link LinkedAuthorizationController#linkTransactionV2(RequestWrapper<LinkTransactionRequest>)} </p>
     *
     * @param requestWrapper
     * @return
     * @throws EsignetException
     */
    @Deprecated()
    @PostMapping("/link-transaction")
    public ResponseWrapper<LinkTransactionResponse> linkTransaction(@Valid @RequestBody RequestWrapper<LinkTransactionRequest>
                                                                            requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
        	responseWrapper.setResponse(linkedAuthorizationService.linkTransaction(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_TRANSACTION, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkCode(), "link-code", null), ex);
            throw ex;
        }        
        return responseWrapper;
    }

    @PostMapping("/v2/link-transaction")
    public ResponseWrapper<LinkTransactionResponseV2> linkTransactionV2(@Valid @RequestBody RequestWrapper<LinkTransactionRequest>
                                                                            requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            responseWrapper.setResponse(linkedAuthorizationService.linkTransactionV2(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_TRANSACTION, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkCode(), "link-code",null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/link-status")
    public DeferredResult<ResponseWrapper<LinkStatusResponse>> getLinkStatus(@Valid @RequestBody RequestWrapper<LinkStatusRequest>
                                                                                     requestWrapper) throws EsignetException {
        DeferredResult deferredResult = new DeferredResult<>(linkStatusDeferredResponseTimeout*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        try {
        	linkedAuthorizationService.getLinkStatus(deferredResult, requestWrapper.getRequest());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_STATUS, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return deferredResult;
    }

    @PostMapping("/authenticate")
    public ResponseWrapper<LinkedKycAuthResponse> authenticate(@Valid @RequestBody RequestWrapper<LinkedKycAuthRequest>
                                                                            requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
        	responseWrapper.setResponse(linkedAuthorizationService.authenticateUser(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_AUTHENTICATE, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkedTransactionId(), "linkTransactionId", null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/v2/authenticate")
    public ResponseWrapper<LinkedKycAuthResponseV2> authenticateV2(@Valid @RequestBody RequestWrapper<LinkedKycAuthRequest>
                                                                       requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            responseWrapper.setResponse(linkedAuthorizationService.authenticateUserV2(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_AUTHENTICATE, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkedTransactionId(), "linkTransactionId",null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/consent")
    public ResponseWrapper<LinkedConsentResponse> saveConsent(@Valid @RequestBody RequestWrapper<LinkedConsentRequest>
                                                                           requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
        	responseWrapper.setResponse(linkedAuthorizationService.saveConsent(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.SAVE_CONSENT, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkedTransactionId(), "linkTransactionId",null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/v2/consent")
    public ResponseWrapper<LinkedConsentResponse> saveConsentV2(@Valid @RequestBody RequestWrapper<LinkedConsentRequestV2>
                                                                      requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
            responseWrapper.setResponse(linkedAuthorizationService.saveConsentV2(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.SAVE_CONSENT, ActionStatus.ERROR,
                    AuditHelper.buildAuditDto(requestWrapper.getRequest().getLinkedTransactionId(), "linkTransactionId",null), ex);
            throw ex;
        }
        return responseWrapper;
    }

    @PostMapping("/send-otp")
    public ResponseWrapper<OtpResponse> sendOtp(@Valid @RequestBody RequestWrapper<OtpRequest>
                                                                          requestWrapper) throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        try {
        	responseWrapper.setResponse(linkedAuthorizationService.sendOtp(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_SEND_OTP, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        
        return responseWrapper;
    }

    @PostMapping("/link-auth-code")
    public DeferredResult<ResponseWrapper<LinkAuthCodeResponse>> getAuthCode(@Valid @RequestBody RequestWrapper<LinkAuthCodeRequest>
                                                                                           requestWrapper) throws EsignetException {
        DeferredResult deferredResult = new DeferredResult<>(linkAuthCodeDeferredResponseTimeout*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        try {
        	linkedAuthorizationService.getLinkAuthCode(deferredResult, requestWrapper.getRequest());
        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.LINK_AUTH_CODE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getTransactionId(), null), ex);
            throw ex;
        }
        return deferredResult;
    }

    private void setTimeoutHandler(DeferredResult deferredResult) {
        deferredResult.onTimeout(new Runnable() {
            @Override
            public void run() {
                ResponseWrapper responseWrapper = new ResponseWrapper();
                responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
                responseWrapper.setErrors(new ArrayList<>());
                responseWrapper.getErrors().add(new Error(ErrorConstants.RESPONSE_TIMEOUT,
                        messageSource.getMessage(ErrorConstants.RESPONSE_TIMEOUT, null, Locale.getDefault())));
                deferredResult.setErrorResult(responseWrapper);
            }
        });
    }

    private void setErrorHandler(DeferredResult deferredResult) {
        deferredResult.onError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                log.error("Building deferred response failed", throwable);
                ResponseWrapper responseWrapper = new ResponseWrapper();
                responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
                responseWrapper.setErrors(new ArrayList<>());
                if(throwable instanceof EsignetException) {
                    String errorCode = ((EsignetException) throwable).getErrorCode();
                    responseWrapper.getErrors().add(new Error(errorCode, messageSource.getMessage(errorCode, null, Locale.getDefault())));
                } else
                    responseWrapper.getErrors().add(new Error(ErrorConstants.UNKNOWN_ERROR,
                            messageSource.getMessage(ErrorConstants.UNKNOWN_ERROR, null, Locale.getDefault())));
                deferredResult.setErrorResult(responseWrapper);
            }
        });
    }

}
