package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthRequest {

    @NotBlank(message = ErrorConstants.INVALID_TRANSACTION_ID)
    private String transactionId;

    @NotBlank(message = ErrorConstants.INVALID_IDENTIFIER)
    private String individualId;

    @NotNull(message = ErrorConstants.INVALID_CHALLENGE_LIST)
    @Size(min = 1, max = 5, message = ErrorConstants.INVALID_CHALLENGE_LIST)
    private List<@Valid AuthChallenge> challengeList;

}
