/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
