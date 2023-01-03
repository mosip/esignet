/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class PublicKeyRegistry {
	
	@Id
	@NotBlank
	@Column(name = "id_hash")
	private String idHash;

	@NotBlank
	@Column(name = "psu_token")
	private String psuToken;
	
	@NotBlank
	@Column(name = "public_key")
	private String publicKey;
	
	@Column(name = "expire_dtimes")
	private LocalDateTime expiredtimes;
	
	@NotBlank
	@Column(name = "wallet_binding_id")
	private String walletBindingId;

	@NotBlank
	@Column(name = "public_key_hash")
	private String publicKeyHash;

	@NotBlank
	@Column(name = "certificate")
	private String certificate;

	@Column(name = "cr_dtimes")
	private LocalDateTime createdtimes;

	@NotBlank
	@Column(name = "auth_factors")
	private String authFactors;

}
