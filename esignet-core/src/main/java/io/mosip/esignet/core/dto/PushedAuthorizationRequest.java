package io.mosip.esignet.core.dto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.*;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import java.io.IOException;
import java.io.Serializable;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLIENT_ID;

@Data
public class PushedAuthorizationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @OIDCScope
    private String scope;

    @OIDCResponseType
    private String response_type;

    @NotBlank(message = INVALID_CLIENT_ID)
    private String client_id;

    @RedirectURL
    private String redirect_uri;

    private String state;

    private String nonce;

    @OIDCDisplay
    private String display;

    @OIDCPrompt
    private String prompt;

    private String acr_values;

    @Valid
    @ClaimsSchema(message = ErrorConstants.INVALID_CLAIM)
    private ClaimsV2 claims;

    private Integer max_age;

    private String claims_locales;

    private String ui_locales;

    private String code_challenge;

    @PKCECodeChallengeMethod
    private String code_challenge_method;

    private String id_token_hint;

    @NotBlank(message = ErrorConstants.INVALID_ASSERTION_TYPE)
    private String client_assertion_type;

    @NotBlank(message =  ErrorConstants.INVALID_ASSERTION)
    private String client_assertion;

    @Null(message = ErrorConstants.INVALID_REQUEST)
    private String request_uri;

    public void setClaims(String claimsJson) {
        if (claimsJson == null || claimsJson.trim().isEmpty()) {
            this.claims = null;
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.claims = mapper.readValue(claimsJson, ClaimsV2.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(ErrorConstants.INVALID_CLAIM, e);
        }
    }
}