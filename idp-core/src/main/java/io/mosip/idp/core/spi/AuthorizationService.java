/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;

public interface AuthorizationService {

    /**
     * All the query parameters of /authorize request are echoed to this request.
     * Resolves and returns auth-factors and claims required to IDP UI.
     * @param oauthDetailRequest
     * @return
     */
    OauthDetailResponse getOauthDetails(String nonce, OauthDetailRequest oauthDetailRequest) throws IdPException;

    /**
     * Request from IDP UI to send OTP to provided individual ID and OTP channel
     * @param otpRequest
     * @return
     */
    OtpResponse sendOtp(OtpRequest otpRequest) throws IdPException;

    /**
     * Authentication request for the required auth-factors
     * @param kycAuthRequest
     * @return
     */
    AuthResponse authenticateUser(KycAuthRequest kycAuthRequest) throws IdPException;

    /**
     * Accepted claims are verified and KYC exchange is performed
     * Redirects to requested redirect_uri
     * @param authCodeRequest
     */
    IdPTransaction getAuthCode(AuthCodeRequest authCodeRequest);
}
