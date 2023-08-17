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
     * @param holderKey
     * @param identityDetails parsed access-token or introspect endpoint response if token is opaque.
     * @return
     */
    VCResult getVerifiableCredential(VCRequestDto vcRequestDto, String holderKey,
                                     Map<String, Object> identityDetails);
}
