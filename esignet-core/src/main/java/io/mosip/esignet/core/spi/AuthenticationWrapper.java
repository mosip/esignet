/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.KycAuthException;
import io.mosip.esignet.core.exception.KycExchangeException;
import io.mosip.esignet.core.exception.SendOtpException;
import io.mosip.esignet.core.dto.*;

import java.util.List;

public interface AuthenticationWrapper {

    /**
     * Delegate request to authenticate the user, and get KYC token
     * @param relyingPartyId relying Party (RP) ID. This ID will be provided during partner self registration process
     * @param clientId OIDC client Id. Auto generated while creating OIDC client in PMS
     * @param kycAuthDto
     * @return KYC Token and Partner specific User Token (PSUT)
     * @throws KycAuthException
     */
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
    List<KycSigningCertificateData> getAllKycSigningCertificates();

}
