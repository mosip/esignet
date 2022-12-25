/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.KeyBindingAuthChallenge;
import io.mosip.idp.core.dto.KeyBindingResult;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.exception.KeyBindingException;
import io.mosip.idp.core.exception.SendOtpException;

import java.util.List;
import java.util.Map;

public interface KeyBindingWrapper {


    SendOtpResult sendBindingOtp(String transactionId, String individualId, List<String> otpChannels,
                                 Map<String, String> requestHeaders) throws SendOtpException;


    KeyBindingResult doKeyBinding(String transactionId, String individualId, List<KeyBindingAuthChallenge> challengeList,
                                  Map<String, Object> jwk, Map<String, String> requestHeaders) throws KeyBindingException;


    void validateBinding(String transactionId, String individualId, AuthChallenge authChallenge);

}
