/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.KeyBindingService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/binding")
public class KeyBindingController {

    @Autowired
    private KeyBindingService keyBindingService;
    
    @PostMapping(value = "binding-otp", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<OtpResponse> sendBindingOtp(@Valid @RequestBody RequestWrapper<BindingOtpRequest> requestWrapper,
                                                       @RequestHeader Map<String, String> headers)
            throws EsignetException {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(keyBindingService.sendBindingOtp(requestWrapper.getRequest(), headers));
        return responseWrapper;
    }
    
    @PostMapping(value = "wallet-binding", consumes = {MediaType.APPLICATION_JSON_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseWrapper<WalletBindingResponse> bindWallet(@Valid @RequestBody RequestWrapper<WalletBindingRequest> requestWrapper,
                                                             @RequestHeader Map<String, String> headers) throws EsignetException {
        ResponseWrapper response = new ResponseWrapper<WalletBindingResponse>();
        response.setResponse(keyBindingService.bindWallet(requestWrapper.getRequest(), headers));
        response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return response;

    }
}
