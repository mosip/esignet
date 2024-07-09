/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.spi;

import java.util.List;
import java.util.Map;

import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.exception.SendOtpException;

public interface Authenticator {

    /**
     * Delegate request to authenticate the user, and get KYC token
     * @param relyingPartyId relying Party (RP) ID. This ID will be provided during partner self registration process
     * @param clientId OIDC client Id. Auto generated while creating OIDC client in PMS
     * @param kycAuthDto
     * @return KYC Token and Partner specific User Token (PSUT)
     * @throws KycAuthException
     */
    @Deprecated
    KycAuthResult doKycAuth(String relyingPartyId, String clientId, KycAuthDto kycAuthDto)
            throws KycAuthException;

    /**
     * Delegate request to exchange KYC token with encrypted user data
     * @param relyingPartyId relying Party (RP) ID. This ID will be provided during partner self registration process
     * @param clientId OIDC client Id. Auto generated while creating OIDC client in PMS
     * @param kycExchangeDto
     * @return signed and encrypted kyc data.
     * @throws KycExchangeException
     */
    KycExchangeResult doKycExchange(String relyingPartyId, String clientId, KycExchangeDto kycExchangeDto)
            throws KycExchangeException;

    /**
     * Delegate request to send out OTP to provided individual Id on the configured channel
     * @param relyingPartyId relying Party (RP) ID. This ID will be provided during partner self registration process
     * @param clientId OIDC client Id. Auto generated while creating OIDC client in PMS
     * @param sendOtpDto
     * @return status of send otp response.
     * @throws SendOtpException
     */
    SendOtpResult sendOtp(String relyingPartyId, String clientId, SendOtpDto sendOtpDto)
            throws SendOtpException;

    /**
     * supported OTP channel to validate in Send-otp request.
     * @return true if supported, otherwise false
     */
    boolean isSupportedOtpChannel(String channel);

    /**
     * Get list of KYC signing certificate and its details.
     * @return list
     */
    List<KycSigningCertificateData> getAllKycSigningCertificates() throws KycSigningCertificateException;

    /**
     * Authenticate and return individual's claims metadata if requested
     * @param relyingPartyId
     * @param clientId
     * @param claimsMetadataRequired
     * @param kycAuthDto
     * @return
     * @throws KycAuthException
     */
    KycAuthResult doKycAuth(String relyingPartyId, String clientId, boolean claimsMetadataRequired, KycAuthDto kycAuthDto)
            throws KycAuthException;

    /**
     * Providioned to return verified userinfo based on the provided verification requirement
     * @param relyingPartyId
     * @param clientId
     * @param kycExchangeDto
     * @return
     * @throws KycExchangeException
     */
    KycExchangeResult doVerifiedKycExchange(String relyingPartyId, String clientId, VerifiedKycExchangeDto kycExchangeDto)
            throws KycExchangeException;
}
