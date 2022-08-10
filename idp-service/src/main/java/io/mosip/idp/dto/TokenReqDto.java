package io.mosip.idp.dto;

import lombok.Data;

@Data
public class TokenReqDto {

    //TODO Need to add DTO validations

    /**
     * Authorization code grant type.
     */
    private String grant_type;

    /**
     * Authorization code, sent as query param in the client's redirect URI.
     */
    private String code;

    /**
     * Client Id of the OIDC client.
     */
    private String client_id;

    /**
     * Type of the client assertion part of this request.
     * Allowed value:
     * urn:ietf:params:oauth:client-assertion-type:jwt-bearer
     */
    private String client_assertion_type;

    /**
     * Private key signed JWT
     */
    private String client_assertion;

}
