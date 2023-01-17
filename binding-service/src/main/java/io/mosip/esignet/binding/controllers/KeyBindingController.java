/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.binding.controllers;

import javax.validation.Valid;

import io.mosip.esignet.binding.services.KeyBindingValidatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import io.mosip.esignet.core.dto.BindingOtpRequest;
import io.mosip.esignet.core.dto.OtpResponse;
import io.mosip.esignet.core.dto.RequestWrapper;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.dto.ValidateBindingRequest;
import io.mosip.esignet.core.dto.ValidateBindingResponse;
import io.mosip.esignet.core.dto.WalletBindingRequest;
import io.mosip.esignet.core.dto.WalletBindingResponse;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.spi.KeyBindingService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/")
public class KeyBindingController {

    @Autowired
    private KeyBindingService keyBindingService;

    @Autowired
    private KeyBindingValidatorService keyBindingValidatorService;
    
    @PostMapping(value = "binding-otp", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<OtpResponse> sendBindingOtp(@Valid @RequestBody RequestWrapper<BindingOtpRequest> requestWrapper,
                                                       @RequestHeader Map<String, String> headers)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(keyBindingService.sendBindingOtp(requestWrapper.getRequest(), headers));
        return responseWrapper;
    }
    
    @PostMapping(value = "wallet-binding", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<WalletBindingResponse> bindWallet(@Valid @RequestBody RequestWrapper<WalletBindingRequest> requestWrapper,
                                                             @RequestHeader Map<String, String> headers) throws IdPException {
    	  ResponseWrapper response = new ResponseWrapper<WalletBindingResponse>();
          response.setResponse(keyBindingService.bindWallet(requestWrapper.getRequest(), headers));
          response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
          return response;
    	
    }
    
    @PostMapping(value = "validate-binding", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<ValidateBindingResponse> validateBinding(@Valid @RequestBody RequestWrapper<ValidateBindingRequest> requestWrapper)
            throws IdPException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(keyBindingValidatorService.validateBinding(requestWrapper.getRequest()));
        return responseWrapper;
    }
}
