/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.dto.*;

public interface AuthorizationService {

    /**
     * All the query parameters of /authorize request are echoed to this request.
     * Resolves and returns auth-factors and claims required to IDP UI.
     * @param oauthDetailRequest
     * @return
     */
    OAuthDetailResponse getOauthDetails(OAuthDetailRequest oauthDetailRequest) throws IdPException;

    /**
     * Request from IDP UI to send OTP to provided individual ID and OTP channel
     * @param otpRequest
     * @return
     */
    OtpResponse sendOtp(OtpRequest otpRequest) throws IdPException;

    /**
     * Authentication request for the required auth-factors
     * @param authRequest
     * @return
     */
    AuthResponse authenticateUser(AuthRequest authRequest) throws IdPException;

    /**
     * Accepted claims are verified and KYC exchange is performed
     * Redirects to requested redirect_uri
     * @param authCodeRequest
     */
    AuthCodeResponse getAuthCode(AuthCodeRequest authCodeRequest) throws IdPException;
}
