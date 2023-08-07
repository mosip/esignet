package io.mosip.esignet.core.dto.vci;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CredentialRequest {

    /**
     * REQUIRED. Format of the Credential to be issued.
     */
    private String format;

    /**
     * OPTIONAL.
     * JSON object containing proof of possession of the key material the issued Credential shall be bound to.
     */
    private CredentialProof proof;

    /**
     * "format": "jwt_vc_json-ld" | "ldp_vc"
     * REQUIRED
     * JSON object containing (and isolating) the detailed description of the credential type.
     * This object MUST be processed using full JSON-LD processing.
     * It consists of the following sub claims:
     * @context: REQUIRED. JSON array
     * types: REQUIRED. JSON array. This claim contains the type values the Wallet shall request
     * in the subsequent Credential Request.
     */
    private CredentialDefinition credential_definition;

    /**
     * "format": "jwt_vc_json"
     * REQUIRED
     * The credential issued by the issuer MUST at least contain the values listed in this claim.
     */
    private List<String> types;

    /**
     * "format": "jwt_vc_json"
     * OPTIONAL. A JSON object containing a list of key value pairs,
     * where the key identifies the claim offered in the Credential.
     * The value MAY be a dictionary, which allows to represent the full (potentially deeply nested)
     * structure of the verifiable credential to be issued.
     * This object determines the optional claims to be added to the credential to be issued.
     */
    private Map<String, Object> credentialSubject;
}
