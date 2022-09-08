/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import io.mosip.idp.core.validator.AuthContextRef;
import io.mosip.idp.core.validator.OIDCClientAuth;
import io.mosip.idp.core.validator.OIDCGrantType;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.*;
import java.util.List;

@Data
public class ClientDetailUpdateRequest {

    @NotBlank
    @URL
    private String logoUri;

    @NotNull
    @Size(min = 1)
    private List<@NotBlank String> redirectUris;

    @NotNull
    @Size(min = 1)
    private List<@NotBlank String> userClaims;

    @NotNull
    @Size(min = 1)
    private List<@NotNull @AuthContextRef String> authContextRefs;

    @NotBlank
    @Pattern(regexp = "^(ACTIVE)|(INACTIVE)$")
    private String status;

    @NotNull
    @Size(min = 1)
    private List<@OIDCGrantType String> grantTypes;

    @NotBlank
    private String clientName;

    @NotNull
    @Size(min = 1)
    private List<@OIDCClientAuth String> clientAuthMethods;
}
