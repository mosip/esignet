package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.UserConsent;
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
    Optional<ConsentDetail> getUserConsent(UserConsentRequest userConsentRequest);

    /**
     * Api to Add User Consent data in Consent Registry
     *
     * @param userConsent consentRequest Object
     * @return {@link ConsentDetail} Consent Response Object after saving the consent to registry.
     *
     */
    ConsentDetail saveUserConsent(UserConsent userConsent) throws EsignetException;

    /**
     * Api to delete user consent from Consent Registry
     * @param psuToken
     * @param clientId
     */
    void deleteUserConsent(String clientId, String psuToken);
}
