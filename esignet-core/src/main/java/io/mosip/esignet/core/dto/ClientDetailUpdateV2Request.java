/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.AuthContextRef;
import io.mosip.esignet.core.validator.OIDCClaim;
import io.mosip.esignet.core.validator.OIDCClientAuth;
import io.mosip.esignet.core.validator.OIDCGrantType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientDetailUpdateV2Request {

    @NotBlank(message = ErrorConstants.INVALID_URI)
    @URL(message = ErrorConstants.INVALID_URI)
    private String logoUri;

    @NotNull(message = ErrorConstants.INVALID_REDIRECT_URI)
    @Size(message = ErrorConstants.INVALID_REDIRECT_URI, min = 1, max = 5)
    private List<@NotBlank(message = ErrorConstants.INVALID_REDIRECT_URI)
    @URL(message = ErrorConstants.INVALID_REDIRECT_URI) String> redirectUris;

    @NotNull(message = ErrorConstants.INVALID_CLAIM)
    @Size(message = ErrorConstants.INVALID_CLAIM, min = 1, max = 30)
    private List<@OIDCClaim String> userClaims;

    @NotNull(message = ErrorConstants.INVALID_ACR)
    @Size(message = ErrorConstants.INVALID_ACR, min = 1, max = 30)
    private List<@AuthContextRef String> authContextRefs;

    @NotNull(message = ErrorConstants.INVALID_STATUS)
    @Pattern(regexp = "^(ACTIVE)|(INACTIVE)$", message = ErrorConstants.INVALID_STATUS)
    private String status;

    @NotNull(message = ErrorConstants.INVALID_GRANT_TYPE)
    @Size(message = ErrorConstants.INVALID_GRANT_TYPE, min = 1, max=3)
    private List<@OIDCGrantType String> grantTypes;

    @NotEmpty(message = ErrorConstants.INVALID_CLIENT_NAME_MAP)
    private Map<@NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_KEY) String,
            @NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_VALUE) String> clientName;

    @NotNull(message = ErrorConstants.INVALID_CLIENT_AUTH)
    @Size(message = ErrorConstants.INVALID_CLIENT_AUTH, min = 1, max = 3)
    private List<@OIDCClientAuth String> clientAuthMethods;
}
