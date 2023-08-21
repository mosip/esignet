/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import org.springframework.web.context.request.async.DeferredResult;

public interface LinkedAuthorizationService {

    /**
     * Validates the provided transactionId and generates a link-code with a ttl.
     * @param linkCodeRequest
     * @return
     */
    LinkCodeResponse generateLinkCode(LinkCodeRequest linkCodeRequest) throws EsignetException;;

    /**
     * Starts a linked-transaction for the provided valid and active link-code.
     * The started linked-transaction is identified with linked-transaction-id
     * @param linkTransactionRequest
     * @return
     */
    LinkTransactionResponse linkTransaction(LinkTransactionRequest linkTransactionRequest) throws EsignetException;

    /**
     * Starts a linked-transaction for the provided valid and active link-code.
     * The started linked-transaction is identified with linked-transaction-id
     * 
     * This response will contain map instead of string in clientName, which
     * contain client's name in multiple language, where key is the language code
     * and the default client name is provided as value for the key @none
     * @param linkTransactionRequest
     * @return
     */
    LinkTransactionResponseV2 linkTransactionV2(LinkTransactionRequest linkTransactionRequest) throws EsignetException;

    /**
     * Returns the status of linked-transaction if any.
     * @param linkStatusRequest
     * @return
     */
    void getLinkStatus(DeferredResult deferredResult, LinkStatusRequest linkStatusRequest) throws EsignetException;

    /**
     * Returns the authentication and consent status of the linked-transaction.
     * @param linkAuthCodeRequest
     * @return
     */
    void getLinkAuthCode(DeferredResult deferredResult, LinkAuthCodeRequest linkAuthCodeRequest) throws EsignetException;

    /**
     * Request from IDP UI to send OTP to provided individual ID and OTP channel
     * @param otpRequest
     * @return
     */
    OtpResponse sendOtp(OtpRequest otpRequest) throws EsignetException;

    /**
     * Authentication request for the required auth-factors
     * @param linkedKycAuthRequest
     * @return
     */
    LinkedKycAuthResponse authenticateUser(LinkedKycAuthRequest linkedKycAuthRequest) throws EsignetException;

    /**
     * Authentication request for the required auth-factors
     * @param linkedKycAuthRequest
     * @return
     */
    LinkedKycAuthResponseV2 authenticateUserV2(LinkedKycAuthRequest linkedKycAuthRequest) throws EsignetException;

    /**
     * Accepted claims are verified and KYC exchange is performed
     * Redirects to requested redirect_uri
     * @param linkedConsentRequest
     */
    LinkedConsentResponse saveConsent(LinkedConsentRequest linkedConsentRequest) throws EsignetException;

    /**
     * Accepted claims are verified and KYC exchange is performed
     * Redirects to requested redirect_uri
     * @param linkedConsentRequest
     */
    LinkedConsentResponse saveConsentV2(LinkedConsentRequestV2 linkedConsentRequest) throws EsignetException;

}
