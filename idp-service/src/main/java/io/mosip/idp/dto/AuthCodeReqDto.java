package io.mosip.idp.dto;

import io.mosip.idp.util.ErrorConstants;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class AuthCodeReqDto {

    @NotNull(message = ErrorConstants.INVALID_REQUEST)
    @NotBlank(message = ErrorConstants.INVALID_REQUEST)
    private String transactionId;

    @NotNull(message = ErrorConstants.INVALID_REQUEST)
    private List<String> acceptedClaims;
}
