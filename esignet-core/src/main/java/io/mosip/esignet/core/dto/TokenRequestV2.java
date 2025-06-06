package io.mosip.esignet.core.dto;

import lombok.Data;

@Data
public class TokenRequestV2 extends TokenRequest{
    /**
     * The code verifier for the PKCE request.
     */
    private String code_verifier;
}
