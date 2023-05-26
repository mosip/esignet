package io.mosip.esignet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.annotation.Id;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.mosip.esignet.core.constants.ErrorConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Consent {


    @Id
    @NotBlank
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = INVALID_CLIENT_ID)
    @Column(name = "client_id")
    private String clientId;

    @NotBlank
    @Column(name = "psu_value")
    private String psuValue;

    @NotBlank(message = INVALID_CLAIM)
    @Column(name = "claims")
    private String claims;

    @NotBlank
    @Column(name = "authorization_scopes")
    private String authorizationScopes;

    @NotBlank
    @Column(name = "created_on")
    private LocalDateTime createdOn;

    @NotBlank
    @Column(name = "expiration")
    private LocalDateTime expiration;

    @Column(name = "signature")
    private String signature;

    @NotBlank
    @Column(name = "hash")
    private String hash;

}
