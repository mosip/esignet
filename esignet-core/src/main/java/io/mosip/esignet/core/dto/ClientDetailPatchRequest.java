/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

/**
 * DTO for PATCH client request. Independent class with all updatable fields.
 * All fields are optional - only provided fields will be updated.
 * Note: Client ID is immutable and cannot be updated.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientDetailPatchRequest {

    @URL(message = ErrorConstants.INVALID_URI)
    private String logoUri;

    @Size(message = ErrorConstants.INVALID_REDIRECT_URI, min = 1, max = 5)
    private List<@NotBlank(message = ErrorConstants.INVALID_REDIRECT_URI)
            @RedirectURL String> redirectUris;

    @Size(message = ErrorConstants.INVALID_CLAIM, min = 1, max = 30)
    private List<@OIDCClaim String> userClaims;

    @Size(message = ErrorConstants.INVALID_ACR, min = 1, max = 30)
    private List<@AuthContextRef String> authContextRefs;

    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = ErrorConstants.INVALID_STATUS)
    private String status;

    @Size(message = ErrorConstants.UNSUPPORTED_GRANT_TYPE, min = 1, max = 3)
    private List<@OIDCGrantType String> grantTypes;

    @Size(max = 256, message = ErrorConstants.INVALID_CLIENT_NAME)
    private String clientName;

    @Size(message = ErrorConstants.INVALID_CLIENT_AUTH, min = 1, max = 3)
    private List<@OIDCClientAuth String> clientAuthMethods;

    private Map<@ClientNameLang String,
            @NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_VALUE) @Size(max = 50,
                    message = ErrorConstants.INVALID_CLIENT_NAME_LENGTH) String> clientNameLangMap;

    @ClientAdditionalConfig
    private JsonNode additionalConfig;

    private Map<String, Object> encPublicKey;
}
