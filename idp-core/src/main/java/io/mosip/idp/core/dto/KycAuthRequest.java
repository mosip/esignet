/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import io.mosip.idp.core.util.ErrorConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KycAuthRequest {

    @NotBlank(message = ErrorConstants.INVALID_TRANSACTION_ID)
    private String transactionId;

    @NotBlank(message = ErrorConstants.INVALID_IDENTIFIER)
    private String individualId;

    @NotNull(message = ErrorConstants.INVALID_CHALLENGE_LIST)
    @Size(min = 1, max = 5, message = ErrorConstants.INVALID_CHALLENGE_LIST)
    private List<AuthChallenge> challengeList;
}
