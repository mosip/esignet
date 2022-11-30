/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.LinkedAuthorizationService;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/linked-authorization")
public class LinkedAuthorizationController {

    @Autowired
    private LinkedAuthorizationService linkedAuthorizationService;


    @PostMapping("/link-transaction")
    public ResponseWrapper<LinkTransactionResponse> linkTransaction(@Valid @RequestBody RequestWrapper<LinkTransactionRequest>
                                                                            requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkedAuthorizationService.linkTransaction(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/authenticate")
    public ResponseWrapper<LinkedKycAuthResponse> authenticate(@Valid @RequestBody RequestWrapper<LinkedKycAuthRequest>
                                                                            requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkedAuthorizationService.authenticateUser(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/consent")
    public ResponseWrapper<LinkedConsentResponse> saveConsent(@Valid @RequestBody RequestWrapper<LinkedConsentRequest>
                                                                           requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkedAuthorizationService.saveConsent(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/send-otp")
    public ResponseWrapper<OtpResponse> sendOtp(@Valid @RequestBody RequestWrapper<OtpRequest>
                                                                          requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkedAuthorizationService.sendOtp(requestWrapper.getRequest()));
        return responseWrapper;
    }

}
