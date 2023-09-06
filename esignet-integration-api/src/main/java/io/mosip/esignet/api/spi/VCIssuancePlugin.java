package io.mosip.esignet.api.spi;

import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.VCRequestDto;
import io.mosip.esignet.api.dto.VCResult;

import java.util.Map;

/**
 *
 */
public interface VCIssuancePlugin {

    /**
     * Applicable for formats : ldp_vc
     * @param vcRequestDto
     * @param holderId Holders key material as either DID / KID. This should be used for cryptographic binding of the VC
     * @param identityDetails Parsed access-token or introspect endpoint response if token is opaque.
     * @return
     */
    VCResult<JsonLDObject> getVerifiableCredentialWithLinkedDataProof(VCRequestDto vcRequestDto, String holderId,
                                                                      Map<String, Object> identityDetails);

    /**
     * Applicable for formats : jwt_vc_json, jwt_vc_json-ld, mso_doc
     * @param vcRequestDto
     * @param holderId
     * @param identityDetails
     * @return
     */
    VCResult<String> getVerifiableCredential(VCRequestDto vcRequestDto, String holderId,
                                                                             Map<String, Object> identityDetails);
}
