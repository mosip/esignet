package io.mosip.idp.core.dto;

import io.mosip.idp.core.util.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class AuthCodeRequest {

    @NotNull(message = ErrorConstants.INVALID_REQUEST)
    @NotBlank(message = ErrorConstants.INVALID_REQUEST)
    private String transactionId;

    /**
     * List of accepted claim names by end-user
     */
    private List<String> acceptedClaims;

    /**
     * List of permitted authorize scopes
     */
    private List<String> permittedAuthorizeScopes;
}
