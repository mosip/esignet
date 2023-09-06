package io.mosip.esignet.core.dto.vci;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CredentialRequest {

    /**
     * REQUIRED. Format of the Credential to be issued.
     */
    @NotBlank(message = ErrorConstants.INVALID_VC_FORMAT)
    private String format;

    /**
     * OPTIONAL.
     * JSON object containing proof of possession of the key material the issued Credential shall be bound to.
     */
    @Valid
    @NotNull(message = ErrorConstants.INVALID_PROOF)
    private CredentialProof proof;

    /**
     * "format": jwt_vc_json | jwt_vc_json-ld | ldp_vc
     * REQUIRED
     * JSON object containing (and isolating) the detailed description of the credential type.
     * This object MUST be processed using full JSON-LD processing.
     * It consists of the following sub claims:
     * @context: REQUIRED. JSON array
     * types: REQUIRED. JSON array. This claim contains the type values the Wallet shall request
     * in the subsequent Credential Request.
     */
    @Valid
    @NotNull(message = ErrorConstants.INVALID_REQUEST)
    private CredentialDefinition credential_definition;
}
