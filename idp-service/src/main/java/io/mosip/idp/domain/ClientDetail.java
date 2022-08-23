/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetail {

    @Id
    private String id;

    @NotBlank(message = "invalid_name")
    @Column(name = "name")
    private String name;

    @NotBlank(message = "invalid_rp")
    @Column(name = "rp_id")
    private String rpId;

    @Column(name = "logo_uri")
    private String logoUri;

    @NotBlank(message = "invalid_redirect_uri")
    @Column(name = "redirect_uris")
    private String redirectUris;

    @NotBlank(message = "invalid_public_key")
    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;//JWKs

    @NotBlank(message = "invalid_claims")
    @Column(name = "claims")
    private String claims;

    @NotBlank(message = "invalid_acr_values")
    @Column(name = "acr_values")
    private String acrValues;

    @NotBlank(message = "invalid_status")
    @Column(name = "status")
    private String status;

    @NotBlank(message = "invalid_grant_types")
    @Column(name = "grant_types")
    private String grantTypes;
}
