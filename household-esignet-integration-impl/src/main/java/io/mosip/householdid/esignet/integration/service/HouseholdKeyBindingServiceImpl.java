package io.mosip.householdid.esignet.integration.service;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.KeyBinder;
import io.mosip.householdid.esignet.integration.util.ErrorConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.esignet.integration.key-binder",havingValue = "HouseHoldKeyBinder")
@Component
@Slf4j
public class HouseholdKeyBindingServiceImpl implements KeyBinder {
    @Override
    public SendOtpResult sendBindingOtp(String individualId, List<String> otpChannels, Map<String, String> requestHeaders) throws SendOtpException {
        throw new SendOtpException("not_implemented");
    }

    @Override
    public KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList, Map<String, Object> publicKeyJWK, String bindAuthFactorType, Map<String, String> requestHeaders) throws KeyBindingException {
        throw new KeyBindingException("not_implemented");
    }

    @Override
    public List<String> getSupportedChallengeFormats(String authFactorType) {
        return Collections.EMPTY_LIST;
    }
}
