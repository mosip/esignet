/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.AuthorizationService;
import io.mosip.idp.core.util.IdentityProviderUtil;
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
    public ResponseWrapper<AuthResponse> authenticateEndUser(@Valid @RequestBody RequestWrapper<AuthRequest>
                                                                        requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(authorizationService.authenticateUser(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/auth-code")
    public ResponseWrapper<AuthCodeResponse> getAuthorizationCode(@Valid @RequestBody RequestWrapper<AuthCodeRequest>
                                                                              requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(authorizationService.getAuthCode(requestWrapper.getRequest()));
        return responseWrapper;
    }
}
