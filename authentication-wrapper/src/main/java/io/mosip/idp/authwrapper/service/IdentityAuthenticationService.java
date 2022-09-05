/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(value = "mosip.idp.authn.wrapper.impl", havingValue = "IdentityAuthenticationService")
@Component
public class IdentityAuthenticationService implements AuthenticationWrapper {

    private static final Logger logger = LoggerFactory.getLogger(IdentityAuthenticationService.class);

    @Override
    public ResponseWrapper<KycAuthResponse> doKycAuth(String licenseKey, String relayingPartnerId,
                                                     String clientId, KycAuthRequest kycAuthRequest) {
        return null;
    }

    @Override
    public String doKycExchange(KycExchangeRequest kycExchangeRequest) {
        return null;
    }

    @Override
    public SendOtpResult sendOtp(String individualId, String channel) {
        return null;
    }
}
