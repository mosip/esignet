package io.mosip.esignet.core.dto.vci;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class VCError {

    private String error;
    private String error_description;

    /**
     * JSON string containing a nonce to be used to create a proof of possession of key material when requesting a Credential
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String c_nonce;

    /**
     *  JSON integer denoting the lifetime in seconds of the c_nonce.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer c_nonce_expires_in;
}
