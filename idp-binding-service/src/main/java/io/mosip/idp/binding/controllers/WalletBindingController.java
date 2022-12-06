/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.idp.core.dto.BindingOtpRequest;
import io.mosip.idp.core.dto.OtpResponse;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.dto.ResponseWrapper;
import io.mosip.idp.core.dto.ValidateBindingRequest;
import io.mosip.idp.core.dto.ValidateBindingResponse;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.dto.WalletBindingResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.WalletBindingService;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/")
public class WalletBindingController {

    @Autowired
    private WalletBindingService walletBindingService;
    
    @PostMapping(value = "send-binding-otp", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<OtpResponse> sendBindingOtp(@Valid @RequestBody RequestWrapper<BindingOtpRequest> requestWrapper)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(walletBindingService.sendBindingOtp(requestWrapper.getRequest()));
        return responseWrapper;
    }
    
    @PostMapping(value = "wallet-binding", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<WalletBindingResponse> bindWallet(@Valid @RequestBody RequestWrapper<WalletBindingRequest> requestWrapper) throws IdPException {
    	  ResponseWrapper response = new ResponseWrapper<WalletBindingResponse>();
          response.setResponse(walletBindingService.bindWallet(requestWrapper.getRequest()));
          response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
          return response;
    	
    }
    
    @PostMapping(value = "validate-binding", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<ValidateBindingResponse> validateBinding(@Valid @RequestBody RequestWrapper<ValidateBindingRequest> requestWrapper)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(walletBindingService.validateBinding(requestWrapper.getRequest()));
        return responseWrapper;
    }
}
