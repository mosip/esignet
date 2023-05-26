package io.mosip.esignet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Consent {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "psu_value", nullable = false)
    private String psuValue;

    @Column(name = "claims", nullable = false)
    private String claims;

    @Column(name = "authorization_scopes", nullable = false)
    private String authorizationScopes;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "expiration")
    private LocalDateTime expiration;

    @Column(name = "signature")
    private String signature;

    @Column(name = "hash")
    private String hash;

    @Column(name = "signed_by")
    private String signedBy;

}
