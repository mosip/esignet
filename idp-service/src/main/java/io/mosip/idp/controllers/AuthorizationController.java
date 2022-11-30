/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.dto.Error;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.AuthorizationService;
import io.mosip.idp.core.spi.LinkedAuthorizationService;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

@Slf4j
@RestController
@RequestMapping("/authorization")
public class AuthorizationController {

    @Autowired
    AuthorizationService authorizationService;

    @Autowired
    LinkedAuthorizationService linkAuthorizationService;

    @Autowired
    MessageSource messageSource;

    @Value("${mosip.idp.link-transaction-wait-in-secs:60}")
    private long linkStatusDeferredResponseTimeout;

    @Value("${mosip.idp.link-auth-code-wait-in-secs:60}")
    private long linkAuthCodeDeferredResponseTimeout;

    @PostMapping("/oauth-details")
    public ResponseWrapper<OAuthDetailResponse> getOauthDetails(@Valid @RequestBody RequestWrapper<OAuthDetailRequest>
                                                                            requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(authorizationService.getOauthDetails(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/send-otp")
    public ResponseWrapper<OtpResponse> sendOtp(@Valid @RequestBody RequestWrapper<OtpRequest> requestWrapper)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(authorizationService.sendOtp(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/authenticate")
    public ResponseWrapper<AuthResponse> authenticateEndUser(@Valid @RequestBody RequestWrapper<KycAuthRequest>
                                                                        requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(authorizationService.authenticateUser(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/auth-code")
    public ResponseWrapper<AuthCodeResponse> getAuthorizationCode(@Valid @RequestBody RequestWrapper<AuthCodeRequest>
                                                                              requestWrapper) throws IdPException {
        IdPTransaction idPTransaction = authorizationService.getAuthCode(requestWrapper.getRequest());
        AuthCodeResponse authCodeResponse = new AuthCodeResponse();
        authCodeResponse.setCode(idPTransaction.getCode());
        authCodeResponse.setRedirectUri(idPTransaction.getRedirectUri());
        authCodeResponse.setNonce(idPTransaction.getNonce());
        authCodeResponse.setState(idPTransaction.getState());
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(authCodeResponse);
        return responseWrapper;
    }

    @PostMapping("/link-code")
    public ResponseWrapper<LinkCodeResponse> generateLinkCode(@Valid @RequestBody RequestWrapper<LinkCodeRequest>
                                                                      requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkAuthorizationService.generateLinkCode(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/link-status")
    public DeferredResult<ResponseWrapper<LinkStatusResponse>> getLinkStatus(@Valid @RequestBody RequestWrapper<LinkStatusRequest>
                                                                      requestWrapper) throws IdPException {
        DeferredResult deferredResult = new DeferredResult<>(linkStatusDeferredResponseTimeout*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        linkAuthorizationService.getLinkStatus(deferredResult, requestWrapper.getRequest());
        return deferredResult;
    }

    @PostMapping("/link-auth-code")
    public DeferredResult<ResponseWrapper<LinkAuthCodeResponse>> getAuthCodeStatus(@Valid @RequestBody RequestWrapper<LinkAuthCodeRequest>
                                                                      requestWrapper) throws IdPException {
        DeferredResult deferredResult = new DeferredResult<>(linkAuthCodeDeferredResponseTimeout*1000);
        setTimeoutHandler(deferredResult);
        setErrorHandler(deferredResult);
        linkAuthorizationService.getLinkAuthCodeStatus(deferredResult, requestWrapper.getRequest());
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
                if(throwable instanceof IdPException) {
                    String errorCode = ((IdPException) throwable).getErrorCode();
                    responseWrapper.getErrors().add(new Error(errorCode, messageSource.getMessage(errorCode, null, Locale.getDefault())));
                } else
                    responseWrapper.getErrors().add(new Error(ErrorConstants.UNKNOWN_ERROR,
                            messageSource.getMessage(ErrorConstants.UNKNOWN_ERROR, null, Locale.getDefault())));
                deferredResult.setResult(responseWrapper);
            }
        });
    }
}
