package io.mosip.esignet.core.dto.vci;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class CredentialResponse<T> {

    /**
     *  JSON string denoting the format of the issued Credential.
     */
    private String format;

    /**
     * Contains issued Credential. MUST be present when acceptance_token is not returned.
     * MAY be a JSON string or a JSON object, depending on the Credential format.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T credential;

    /**
     * A JSON string containing a security token subsequently used to obtain a Credential.
     *  MUST be present when credential is not returned
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String acceptance_token;

    /**
     *  JSON string containing a nonce to be used to create a proof of possession of key material
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String c_nonce;

    /**
     *  JSON integer denoting the lifetime in seconds of the c_nonce
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer c_nonce_expires_in;
}
