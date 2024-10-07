package io.mosip.esignet.core.util;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.KeyBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.mosip.esignet.api.util.ErrorConstants.NOT_IMPLEMENTED;

@ConditionalOnProperty(value = "mosip.esignet.integration.key-binder", havingValue = "NoOpKeyBinder")
@Component
@Slf4j
public class NoOpKeyBinderImpl implements KeyBinder {

    @Override
    public SendOtpResult sendBindingOtp(String individualId, List<String> otpChannels, Map<String, String> requestHeaders) throws SendOtpException {
        throw new SendOtpException(NOT_IMPLEMENTED);
    }

    @Override
    public KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList, Map<String, Object> publicKeyJWK, String bindAuthFactorType, Map<String, String> requestHeaders) throws KeyBindingException {
        throw new  KeyBindingException(NOT_IMPLEMENTED);
    }

    @Override
    public List<String> getSupportedChallengeFormats(String authFactorType) {
        return List.of();
    }
}
