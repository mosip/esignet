package io.mosip.idp.authwrapper.service;

import io.mosip.idp.core.dto.KycAuthRequest;
import io.mosip.idp.core.dto.KycAuthResponse;
import io.mosip.idp.core.dto.KycExchangeRequest;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(value = "mosip.idp.authn.wrapper.impl",
        havingValue = "IdentityAuthenticationService", matchIfMissing = true)
@Component
public class IdentityAuthenticationService implements AuthenticationWrapper {

    private static final Logger logger = LoggerFactory.getLogger(IdentityAuthenticationService.class);

    @Override
    public <SBIAuthResponse> KycAuthResponse doKycAuth(KycAuthRequest<SBIAuthResponse> kycAuthRequest) {
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
