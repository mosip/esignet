/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;


import io.mosip.idp.dto.*;
import io.mosip.idp.exception.IdPException;
import io.mosip.idp.exception.InvalidClientException;
import io.mosip.idp.services.InternalApiService;
import io.mosip.idp.util.ErrorConstants;
import io.mosip.idp.util.IdentityProviderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Set;

import static io.mosip.idp.util.ErrorConstants.*;

@RestController
@RequestMapping("/internal")
public class InternalApiController {

    private static final Logger logger = LoggerFactory.getLogger(InternalApiController.class);

    @Autowired
    InternalApiService internalApiService;

    @PostMapping("/oauth-details")
    public ResponseWrapper<OauthRespDto> getOauthDetails(@Valid @RequestBody RequestWrapper<OauthReqDto> requestWrapper)
            throws IdPException {
        OauthRespDto oauthRespDto = internalApiService.getOauthDetails(requestWrapper.getRequest());
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getResponseTime());
        responseWrapper.setResponse(oauthRespDto);
        return responseWrapper;
    }

    @PostMapping("/send-otp")
    public ResponseWrapper<OtpRespDto> sendOtp(@Valid @RequestBody RequestWrapper<OtpReqDto> requestWrapper)
            throws IdPException {
        OtpRespDto otpRespDto = internalApiService.sendOtp(requestWrapper.getRequest());
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getResponseTime());
        responseWrapper.setResponse(otpRespDto);
        return responseWrapper;
    }

    @PostMapping("/authenticate")
    public ResponseWrapper<AuthRespDto> authenticateEndUser(@Valid @RequestBody RequestWrapper<AuthReqDto>
                                                                        requestWrapper) throws IdPException {
        AuthRespDto authRespDto = internalApiService.authenticateEndUser(requestWrapper.getRequest());
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getResponseTime());
        responseWrapper.setResponse(authRespDto);
        return responseWrapper;
    }

    @PostMapping("/auth-code")
    public RedirectView getAuthorizationCode(@RequestParam(value = "state", required = true) String state,
                                             @RequestParam(value = "nonce", required = true) String nonce,
                                             @Valid @RequestBody RequestWrapper<AuthCodeReqDto> requestWrapper,
                                             final RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("state", state);
        redirectAttributes.addAttribute("nonce", nonce);
        IdPTransaction idPTransaction = internalApiService.getAuthorizationCode(requestWrapper.getRequest());
        if(idPTransaction.getCode() != null)
            redirectAttributes.addAttribute("code", idPTransaction.getCode());
        else
            redirectAttributes.addAttribute("error", idPTransaction.getError());
        return new RedirectView(idPTransaction.getRedirectUri());
    }
}
