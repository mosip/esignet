package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.vci.CredentialRequest;
import io.mosip.esignet.core.dto.vci.CredentialResponse;

import java.util.Map;

public interface VCIssuanceService {

    /**
     *
     * @param credentialRequest
     * @return
     */
    <T> CredentialResponse<T> getCredential(CredentialRequest credentialRequest);

    Map<String, Object> getCredentialIssuerMetadata(String version);
}
