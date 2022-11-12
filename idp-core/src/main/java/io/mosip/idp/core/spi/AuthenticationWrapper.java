/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.KycExchangeException;
import io.mosip.idp.core.exception.SendOtpException;

import java.util.List;

public interface AuthenticationWrapper {

    /**
     * Delegate request to authenticate the user, and get KYC token
     * @param relyingPartyId relying Party (RP) ID. This ID will be provided during partner self registration process
     * @param clientId OIDC client Id. Auto generated while creating OIDC client in PMS
     * @param kycAuthRequest
     * @return KYC Token and Partner specific User Token (PSUT)
     * @throws KycAuthException
     */
    KycAuthResult doKycAuth(String relyingPartyId, String clientId, KycAuthRequest kycAuthRequest)
            throws KycAuthException;

    /**
     * Delegate request to exchange KYC token with encrypted user data
     * @param relyingPartyId relying Party (RP) ID. This ID will be provided during partner self registration process
     * @param clientId OIDC client Id. Auto generated while creating OIDC client in PMS
     * @param kycExchangeRequest
     * @return signed and encrypted kyc data.
     * @throws KycExchangeException
     */
    KycExchangeResult doKycExchange(String relyingPartyId, String clientId, KycExchangeRequest kycExchangeRequest)
            throws KycExchangeException;

    /**
     * Delegate request to send out OTP to provided individual Id on the configured channel
     * @param relyingPartyId relying Party (RP) ID. This ID will be provided during partner self registration process
     * @param clientId OIDC client Id. Auto generated while creating OIDC client in PMS
     * @param sendOtpRequest
     * @return status of send otp response.
     * @throws SendOtpException
     */
    SendOtpResult sendOtp(String relyingPartyId, String clientId, SendOtpRequest sendOtpRequest)
            throws SendOtpException;

    List<KycSigningCertificateData> getAllKycSigningCertificates();

}
