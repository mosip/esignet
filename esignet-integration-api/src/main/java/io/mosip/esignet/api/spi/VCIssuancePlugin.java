package io.mosip.esignet.api.spi;

import io.mosip.esignet.api.dto.VCRequestDto;
import io.mosip.esignet.api.dto.VCResult;

import java.util.Map;

/**
 *
 */
public interface VCIssuancePlugin {

    /**
     * WIP
     * @param vcRequestDto
     * @param holderId Holders key material as either DID / KID. This should be used for cryptographic binding of the VC
     * @param identityDetails Parsed access-token or introspect endpoint response if token is opaque.
     * @return
     */
    VCResult getVerifiableCredential(VCRequestDto vcRequestDto, String holderId,
                                     Map<String, Object> identityDetails);
}
