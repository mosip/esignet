/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.KycAuthRequest;
import io.mosip.idp.core.dto.KycAuthResponse;
import io.mosip.idp.core.dto.KycExchangeRequest;
import io.mosip.idp.core.dto.SendOtpResult;

public interface AuthenticationWrapper {

    /**
     * Delegate request to authenticate the user, and get KYC token
     * Request to be signed with IdP key, signature to be set in the request header.
     * header name: signature
     *
     * @param kycAuthRequest
     * @return KYC Token and Partner specific User Token (PSUT)
     */
    <T> KycAuthResponse doKycAuth(KycAuthRequest<T> kycAuthRequest);

    /**
     * Delegate request to exchange KYC token with encrypted user data
     * Request to be signed with IdP key, signature to be set in the request header.
     * header name: signature
     *
     * @param kycExchangeRequest
     * @return encrypted KYC data
     */
    String doKycExchange(KycExchangeRequest kycExchangeRequest);

    /**
     * Delegate request to send out OTP to provided individual Id on the configured channel
     * @param individualId either UIN or VID
     * @param channel either sms or email
     * @return send OTP status
     */
    SendOtpResult sendOtp(String individualId, String channel);

}
