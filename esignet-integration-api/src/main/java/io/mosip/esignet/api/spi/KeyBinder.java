/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.spi;

import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;

import java.util.List;
import java.util.Map;

public interface KeyBinder {

    /**
     * Delegate request to send out OTP to provided individual Id on the configured channel
     * during Key binding process.
     * @param individualId
     * @param otpChannels
     * @param requestHeaders
     * @return
     * @throws SendOtpException
     */
    SendOtpResult sendBindingOtp(String individualId, List<String> otpChannels,
                                 Map<String, String> requestHeaders) throws SendOtpException;


    /**
     * Delegate request check the given challenge. Binds the key only if the given challenge is valid
     * returns back the new signed certificate and a partner specific user token.
     * @param individualId
     * @param challengeList
     * @param publicKeyJWK
     * @param requestHeaders
     * @return
     * @throws KeyBindingException
     */
    KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList, Map<String, Object> publicKeyJWK,
                                  String bindAuthFactorType, Map<String, String> requestHeaders) throws KeyBindingException;

    List<String> getSupportedChallengeFormats(String authFactorType);

}
