package io.mosip.esignet.services;

import io.mosip.esignet.core.dto.Consent;
import io.mosip.esignet.core.dto.ConsentRequest;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;

import java.util.Optional;

public class ConsentServiceImpl implements ConsentService {
    @Override
    public Optional<Consent> getUserConsent(UserConsentRequest userConsentRequest) {
        return Optional.empty();
    }

    @Override
    public Consent saveUserConsent(ConsentRequest consentRequest) {
        return null;
    }
}
