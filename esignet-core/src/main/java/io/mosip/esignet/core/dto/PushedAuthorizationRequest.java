package io.mosip.esignet.core.dto;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.*;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLIENT_ID;

@Data
public class PushedAuthorizationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @OIDCScope
    private String scope;

    @OIDCPrompt
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

    private String client_assertion_type;

    private String client_assertion;

}