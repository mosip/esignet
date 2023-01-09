/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.AuthChallenge;
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
    KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList,
                                  Map<String, Object> publicKeyJWK, Map<String, String> requestHeaders) throws KeyBindingException;

    List<String> getSupportedChallengeFormats(String authFactorType);

}
