/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.*;
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
public class ClientDetailCreateRequest {

    @IdFormat(message = ErrorConstants.INVALID_CLIENT_ID)
    @Size(max = 100, message = ErrorConstants.INVALID_CLIENT_ID)
    private String clientId;

    @NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME)
    @Size(max = 256, message = ErrorConstants.INVALID_CLIENT_NAME)
    private String clientName;

    @NotEmpty(message = ErrorConstants.INVALID_PUBLIC_KEY)
    private Map<String, Object> publicKey;

    @IdFormat(message = ErrorConstants.INVALID_RP_ID)
    @Size(max = 100, message = ErrorConstants.INVALID_RP_ID)
    private String relyingPartyId;

    @NotNull(message = ErrorConstants.INVALID_CLAIM)
    @Size(message = ErrorConstants.INVALID_CLAIM, max = 30)
    private List<@OIDCClaim String> userClaims;

    @NotNull(message = ErrorConstants.INVALID_ACR)
    @Size(message = ErrorConstants.INVALID_ACR, min = 1, max = 30)
    private List<@AuthContextRef String> authContextRefs;

    @NotBlank(message = ErrorConstants.INVALID_URI)
    @URL(message = ErrorConstants.INVALID_URI)
    private String logoUri;

    @NotNull(message = ErrorConstants.INVALID_REDIRECT_URI)
    @Size(message = ErrorConstants.INVALID_REDIRECT_URI, min = 1, max = 5)
    private List<@NotBlank(message = ErrorConstants.INVALID_REDIRECT_URI)
                 @RedirectURL String> redirectUris;

    @NotNull(message = ErrorConstants.INVALID_GRANT_TYPE)
    @Size(message = ErrorConstants.INVALID_GRANT_TYPE, min = 1, max=3)
    private List<@OIDCGrantType String> grantTypes;

    @NotNull(message = ErrorConstants.INVALID_CLIENT_AUTH)
    @Size(message = ErrorConstants.INVALID_CLIENT_AUTH, min = 1, max = 3)
    private List<@OIDCClientAuth String> clientAuthMethods;
}
