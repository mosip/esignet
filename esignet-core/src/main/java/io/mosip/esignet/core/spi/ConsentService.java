package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.Consent;
import io.mosip.esignet.core.dto.ConsentRequest;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.exception.EsignetException;

import java.util.Optional;

public interface ConsentService {
    /**
     * Api to get Latest User consent data from consent registry.
     * </ul>
     * @param userConsentRequest Consent Request object containing client_id and psu_token
     * @return the Consent wrapped in an {@link Optional}
     */
    Optional<Consent> getUserConsent(UserConsentRequest userConsentRequest);

    /**
     * Api to Add User Consent data in Consent Registry
     *
     * @param consentRequest consentRequest Object
     * @return {@link Consent} Consent Response Object after saving the consent to registry.
     *
     */
    Consent saveUserConsent(ConsentRequest consentRequest) throws EsignetException;
}
