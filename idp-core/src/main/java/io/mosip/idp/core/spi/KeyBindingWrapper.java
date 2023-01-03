/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.KeyBindingAuthChallenge;
import io.mosip.idp.core.dto.KeyBindingResult;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.exception.KeyBindingException;
import io.mosip.idp.core.exception.SendOtpException;

import java.util.List;
import java.util.Map;

public interface KeyBindingWrapper {

    /**
     * Delegate request to send out OTP to provided individual Id on the configured channel
     * during Key binding process.
     * @param transactionId
     * @param individualId
     * @param otpChannels
     * @param requestHeaders
     * @return
     * @throws SendOtpException
     */
    SendOtpResult sendBindingOtp(String transactionId, String individualId, List<String> otpChannels,
                                 Map<String, String> requestHeaders) throws SendOtpException;


    /**
     * Delegate request check the given challenge. Binds the key only if the given challenge is valid
     * returns back the new signed certificate and a partner specific user token.
     * @param transactionId
     * @param individualId
     * @param challengeList
     * @param jwk
     * @param requestHeaders
     * @return
     * @throws KeyBindingException
     */
    KeyBindingResult doKeyBinding(String transactionId, String individualId, List<KeyBindingAuthChallenge> challengeList,
                                  Map<String, Object> jwk, Map<String, String> requestHeaders) throws KeyBindingException;

}
