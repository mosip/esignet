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
     * @return
     */
    <T> KycAuthResponse doKycAuth(KycAuthRequest<T> kycAuthRequest);

    String doKycExchange(KycExchangeRequest kycExchangeRequest);

    SendOtpResult sendOtp(String individualId, String channel);

}
