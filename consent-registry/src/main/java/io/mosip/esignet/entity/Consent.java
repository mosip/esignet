package io.mosip.esignet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import static io.mosip.esignet.core.constants.ErrorConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Consent {
    @Id
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

    @NotNull
    @Column(name = "created_on")
    private LocalDateTime createdOn;

    @NotNull
    @Column(name = "expiration")
    private LocalDateTime expiration;

    @Column(name = "signature")
    private String signature;

    @NotBlank
    @Column(name = "hash")
    private String hash;

}
