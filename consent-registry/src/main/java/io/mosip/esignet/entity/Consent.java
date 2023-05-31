package io.mosip.esignet.entity;

import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLIENT_ID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Consent {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = INVALID_CLIENT_ID)
    @Column(name = "client_id")
    private String clientId;

    @NotNull
    @Column(name = "psu_value")
    private String psuValue;

    @NotNull(message = INVALID_CLAIM)
    @Column(name = "claims")
    private String claims;

    @NotNull
    @Column(name = "authorization_scopes")
    private String authorizationScopes;

    @NotNull
    @Column(name = "created_on")
    private LocalDateTime createdOn;

    @Column(name = "expiration")
    private LocalDateTime expiration;

    @Column(name = "signature")
    private String signature;

    @Column(name = "hash")
    private String hash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Consent consent = (Consent) o;
        return getId() != null && Objects.equals(getId(), consent.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
