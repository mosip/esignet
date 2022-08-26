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
     * Request to be signed with IdP key, signature to be set in the request header.
     * header name: signature
     *
     * @param kycAuthRequest
     * @return KYC Token and Partner specific User Token (PSUT)
     */
    <T> KycAuthResponse doKycAuth(KycAuthRequest<T> kycAuthRequest);

    /**
     * Request to be signed with IdP key, signature to be set in the request header.
     * header name: signature
     *
     * @param kycExchangeRequest
     * @return encrypted KYC data
     */
    String doKycExchange(KycExchangeRequest kycExchangeRequest);

    SendOtpResult sendOtp(String individualId, String channel);

}
