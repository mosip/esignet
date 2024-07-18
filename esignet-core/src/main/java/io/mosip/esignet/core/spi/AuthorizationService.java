/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;

public interface AuthorizationService {

    /**
     * All the query parameters of /authorize request are echoed to this request.
     * Resolves and returns auth-factors and claims required to IDP UI.
     * @param oauthDetailRequest
     * @return
     */
    OAuthDetailResponseV1 getOauthDetails(OAuthDetailRequest oauthDetailRequest) throws EsignetException;

    /**
     * All the query parameters of /authorize request are echoed to this request.
     * Resolves and returns auth-factors and claims required to IDP UI.
     * 
     * This response will contain map instead of string in clientName, which
     * contain client's name in multiple language, where key is the language code
     * and the default client name is provided as value for the key @none.
     * PKCE support added.
     * @param oAuthDetailRequestV2
     * @return
     */
    OAuthDetailResponseV2 getOauthDetailsV2(OAuthDetailRequestV2 oAuthDetailRequestV2) throws EsignetException;

    /**
     * All the query parameters passed in /authorize request are echoed to this request.
     * Validated the provided input and resolves required auth-factors and claims.
     * If all the input parameters pass the validation, starts the transaction.
     *
     * @param oAuthDetailRequestV3
     * @param httpServletRequest
     * @return
     * @throws EsignetException
     */
    OAuthDetailResponseV2 getOauthDetailsV3(OAuthDetailRequestV3 oAuthDetailRequestV3, HttpServletRequest httpServletRequest) throws EsignetException;

    /**
     * Request from IDP UI to send OTP to provided individual ID and OTP channel
     * @param otpRequest
     * @return
     */
    OtpResponse sendOtp(OtpRequest otpRequest) throws EsignetException;

    /**
     * Authentication request for the required auth-factors
     * @param authRequest
     * @return
     */
    AuthResponse authenticateUser(AuthRequest authRequest) throws EsignetException;

    /**
     * Authentication request for the required auth-factors,
     * Handles check on consent registry to decide whether user can proceed with stored consent or re-consent required.
     * @param authRequest
     * @return
     */
    AuthResponseV2 authenticateUserV2(AuthRequest authRequest) throws EsignetException;

    /**
     * Authentication request for the required auth-factors,
     * Handles check on consent registry to decide whether user can proceed with stored consent or re-consent required.
     * Adds captcha token check only for configured auth-factors.
     * @param authRequest
     * @return
     */
    AuthResponseV2 authenticateUserV3(AuthRequestV2 authRequest, HttpServletRequest httpServletRequest) throws EsignetException;

    /**
     * Accepted claims are verified and KYC exchange is performed
     * Redirects to requested redirect_uri
     * @param authCodeRequest
     */
    AuthCodeResponse getAuthCode(AuthCodeRequest authCodeRequest) throws EsignetException;


    /**
     * Validates the transaction and prepares the ID token and sets up a cookie.
     * ID token sent in the response will be used as hint from signup service
     * @param signupRedirectRequest
     * @param response
     * @return
     */
    SignupRedirectResponse prepareSignupRedirect(SignupRedirectRequest signupRedirectRequest, HttpServletResponse response);

    /**
     * Get the ClaimStatus and check the consent Action
     * @param transactionId
     * @return
     */
    ClaimDetailResponse getClaimDetails(String transactionId);

    /**
     * Resume and get the status of resumed transaction
     * @param resumeRequest
     * @return
     */
    ResumeResponse resumeHaltedTransaction(ResumeRequest resumeRequest);
}
