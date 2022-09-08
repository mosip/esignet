/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(value = "mosip.idp.authn.wrapper.impl", havingValue = "IdentityAuthenticationService")
@Component
@Slf4j
public class IdentityAuthenticationService implements AuthenticationWrapper {

    private static final Logger logger = LoggerFactory.getLogger(IdentityAuthenticationService.class);

    @Override
    public ResponseWrapper<KycAuthResponse> doKycAuth(String licenseKey, String relayingPartnerId,
                                                     String clientId, KycAuthRequest kycAuthRequest) {
        throw new NotImplementedException("KYC auth not implemented");
    }

    @Override
    public ResponseWrapper<KycExchangeResult> doKycExchange(KycExchangeRequest kycExchangeRequest) {
        throw new NotImplementedException("KYC exchange not implemented");
    }

    @Override
    public SendOtpResult sendOtp(String individualId, String channel) {
        throw new NotImplementedException("Send OTP not implemented");
    }
}
