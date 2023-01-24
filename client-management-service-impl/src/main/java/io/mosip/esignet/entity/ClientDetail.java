/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;

import static io.mosip.esignet.core.constants.ErrorConstants.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetail {

    @Id
    @NotBlank
    private String id;

    @NotBlank(message = INVALID_CLIENT_NAME)
    @Column(name = "name")
    private String name;

    @NotBlank(message = INVALID_RP_ID)
    @Column(name = "rp_id")
    private String rpId;

    @NotBlank(message = INVALID_URI)
    @Column(name = "logo_uri")
    private String logoUri;

    @NotBlank(message = INVALID_REDIRECT_URI)
    @Column(name = "redirect_uris")
    private String redirectUris;

    @NotBlank(message = INVALID_PUBLIC_KEY)
    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @NotBlank(message = INVALID_CLAIM)
    @Column(name = "claims")
    private String claims;

    @NotBlank(message = INVALID_ACR)
    @Column(name = "acr_values")
    private String acrValues;

    @Pattern(regexp = "^(ACTIVE)|(INACTIVE)$")
    @Column(name = "status")
    private String status;

    @NotBlank(message = INVALID_GRANT_TYPE)
    @Column(name = "grant_types")
    private String grantTypes;

    @NotBlank(message = INVALID_CLIENT_AUTH)
    @Column(name = "auth_methods")
    private String clientAuthMethods;

    @Column(name = "cr_dtimes")
    private LocalDateTime createdtimes;

    @Column(name = "upd_dtimes")
    private LocalDateTime updatedtimes;
}
