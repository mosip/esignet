package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.vci.CredentialRequest;
import io.mosip.esignet.core.dto.vci.CredentialResponse;

import java.util.Map;

public interface VCIssuanceService {

    /**
     *
     * @param authorizationHeader
     * @param credentialRequest
     * @return
     */
    CredentialResponse getCredential(String authorizationHeader, CredentialRequest credentialRequest);

    Map<String, Object> getCredentialIssuerMetadata();
}
