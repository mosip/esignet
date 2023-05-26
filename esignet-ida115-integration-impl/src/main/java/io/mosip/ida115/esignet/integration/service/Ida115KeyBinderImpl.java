/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.ida115.esignet.integration.service;


import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.KeyBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.esignet.integration.key-binder", havingValue = "Ida115KeyBinderImpl")
@Component
@Slf4j
public class Ida115KeyBinderImpl implements KeyBinder {

    private static final Map<String, List<String>> supportedFormats = new HashMap<>();
    static {
        supportedFormats.put("OTP", Arrays.asList("alpha-numeric"));
        supportedFormats.put("PIN", Arrays.asList("number"));
        supportedFormats.put("BIO", Arrays.asList("encoded-json"));
        //supportedFormats.put("WLA", Arrays.asList("jwt"));
    }

    @Override
    public SendOtpResult sendBindingOtp(String individualId, List<String> otpChannels, Map<String, String> requestHeaders)
            throws SendOtpException {
        throw new SendOtpException("not_implemented");
    }

    @Override
    public KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList, Map<String, Object> publicKeyJWK,
                                         String bindAuthFactorType, Map<String, String> requestHeaders) throws KeyBindingException {
        throw new KeyBindingException("not_implemented");
    }

    @Override
    public List<String> getSupportedChallengeFormats(String authFactorType) {
        return supportedFormats.getOrDefault(authFactorType, Arrays.asList());
    }

}
