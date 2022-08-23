/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import lombok.Data;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Data
public class ClientDetailCreateRequest {

    @NotNull
    @NotBlank
    private String clientId;

    @NotNull
    @NotBlank
    private String clientName;

    @NotNull
    @Size(min = 1)
    private Map<@NotBlank String, Object> publicKey;

    @NotNull
    @NotBlank
    @Pattern(regexp = "^(ACTIVE)|(INACTIVE)$", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String status;

    @NotNull
    @NotBlank
    private String relayingPartyId;

    @NotNull
    @Size(min = 1)
    private List<@NotBlank String> userClaims;

    //MUST be among pre-defined set of values
    @NotNull
    @Size(min = 1)
    private List<@NotBlank String> authContextRefs;

    @NotNull
    @NotBlank
    @URL
    private String logoUri;

    @NotNull
    @Size(min = 1)
    private List<@NotBlank String> redirectUris;

    @NotNull
    @Size(min = 1)
    private List<@NotBlank String> grantTypes;
}
