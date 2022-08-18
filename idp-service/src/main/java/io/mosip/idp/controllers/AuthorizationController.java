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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.validation.Valid;

@RestController
@RequestMapping("/authorization")
public class AuthorizationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationController.class);

    @Autowired
    AuthorizationService authorizationService;

    @PostMapping("/oauth-details")
    public ResponseWrapper<OauthDetailResponse> getOauthDetails(@RequestParam("nonce") String nonce,
                                                                @Valid @RequestBody RequestWrapper<OauthDetailRequest> requestWrapper)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getResponseTime());
        responseWrapper.setResponse(authorizationService.getOauthDetails(nonce, requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/send-otp")
    public ResponseWrapper<OtpResponse> sendOtp(@Valid @RequestBody RequestWrapper<OtpRequest> requestWrapper)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getResponseTime());
        responseWrapper.setResponse(authorizationService.sendOtp(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/authenticate")
    public ResponseWrapper<AuthResponse> authenticateEndUser(@Valid @RequestBody RequestWrapper<KycAuthRequest>
                                                                        requestWrapper) throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getResponseTime());
        responseWrapper.setResponse(authorizationService.authenticateUser(requestWrapper.getRequest()));
        return responseWrapper;
    }

    @PostMapping("/auth-code")
    public RedirectView getAuthorizationCode(@RequestParam(value = "state", required = true) String state,
                                             @RequestParam(value = "nonce", required = true) String nonce,
                                             @Valid @RequestBody RequestWrapper<AuthCodeRequest> requestWrapper,
                                             final RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("state", state);
        redirectAttributes.addAttribute("nonce", nonce);

        IdPTransaction idPTransaction = authorizationService.getAuthCode(requestWrapper.getRequest());
        if(idPTransaction.getError() == null && idPTransaction.getCode() != null) {
            redirectAttributes.addAttribute("code", idPTransaction.getCode());
            redirectAttributes.addAttribute("nonce", idPTransaction.getNonce());
        }
        else
            redirectAttributes.addAttribute("error", idPTransaction.getError());
        return new RedirectView(idPTransaction.getRedirectUri());
    }
}
