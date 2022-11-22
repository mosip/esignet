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
	@Column(name = "individual_id")
	private String individualId;
	
	@NotBlank
	@Column(name = "psu_token")
	private String psuToken;
	
	@NotBlank
	@Column(name = "public_key")
	private String publicKey;
	
	@Column(name = "expires_on")
	private LocalDateTime expiresOn;
	
	@Column(name = "cr_dtimes")
    private LocalDateTime createdtimes;

}
