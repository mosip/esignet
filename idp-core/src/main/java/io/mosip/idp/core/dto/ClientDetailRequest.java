/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import io.mosip.idp.core.validator.OIDCGrantType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class ClientDetailRequest {

    @NotNull
    @NotBlank
    private String clientId;

    @NotNull
    @NotBlank
    private String clientName;

    @NotNull
    @NotBlank
    private String publicKey;

    @NotNull
    @NotBlank
    private String status;

    @NotNull
    @NotBlank
    private String relayingPartyId;

    @NotNull
    @Size(min = 1)
    private List<String> userClaims;

    //MUST be among pre-defined set of values
    @NotNull
    @Size(min = 1)
    private List<String> authContextRefs;

    @NotNull
    @NotBlank
    private String logoUri;

    @NotNull
    @Size(min = 1)
    private List<String> redirectUris;

    @NotNull
    @Size(min = 1)
    private List<String> grantTypes;
}
