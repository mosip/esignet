package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.vci.CredentialRequest;
import io.mosip.esignet.core.dto.vci.CredentialResponse;

public interface VCIssuanceService {

    /**
     *
     * @param authorizationHeader
     * @param credentialRequest
     * @return
     */
    CredentialResponse getCredential(String authorizationHeader, CredentialRequest credentialRequest);
}
