/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import io.mosip.idp.core.dto.KeyBindingAuthChallenge;
import io.mosip.idp.core.dto.KeyBindingResult;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.exception.KeyBindingException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.spi.KeyBindingWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.idp.binding.wrapper.impl", havingValue = "MockKeyBindingWrapperService")
@Component
@Slf4j
public class MockKeyBindingWrapperService implements KeyBindingWrapper {


    @Override
    public SendOtpResult sendBindingOtp(String transactionId, String individualId, List<String> otpChannels,
                                        Map<String, String> requestHeaders) throws SendOtpException {
        SendOtpResult sendOtpResult = new SendOtpResult(transactionId, "", "");
        //TODO
        return sendOtpResult;
    }

    @Override
    public KeyBindingResult doKeyBinding(String transactionId, String individualId, List<KeyBindingAuthChallenge> challengeList,
                                         Map<String, Object> jwk, Map<String, String> requestHeaders) throws KeyBindingException {
        KeyBindingResult keyBindingResult = new KeyBindingResult();
        //TODO
        //create a signed certificate, with cn as username
        //certificate validity based on configuration
        keyBindingResult.setCertificate("");
        keyBindingResult.setPartnerSpecificToken("");
        return keyBindingResult;
    }
}
