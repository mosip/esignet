/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.entity;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLIENT_ID;

@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ConsentDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = INVALID_CLIENT_ID)
    @Column(name = "client_id")
    private String clientId;

    @NotNull
    @Column(name = "psu_token")
    private String psuToken;

    @NotNull(message = INVALID_CLAIM)
    @Column(name = "claims")
    private String claims;

    /*
    It stores the requested authorization scopes from the relying party in a json string
    {
        "scope" : "boolean" (essential or optional)
    }
     */
    @NotNull
    @Column(name = "authorization_scopes")
    private String authorizationScopes;

    @NotNull
    @Column(name = "cr_dtimes")
    private LocalDateTime createdtimes;

    @Column(name = "expire_dtimes")
    private LocalDateTime expiredtimes;

    @Column(name = "signature")
    private String signature;

    @Column(name = "hash")
    private String hash;

    @Column(name = "accepted_claims")
    private String acceptedClaims;

    @Column(name = "permitted_scopes")
    private String permittedScopes;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ConsentDetail consentDetail = (ConsentDetail) o;
        return getId() != null && Objects.equals(getId(), consentDetail.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public @NotNull(message = INVALID_CLIENT_ID) String getClientId() {
        return clientId;
    }

    public void setClientId(@NotNull(message = INVALID_CLIENT_ID) String clientId) {
        this.clientId = clientId;
    }

    public @NotNull String getPsuToken() {
        return psuToken;
    }

    public void setPsuToken(@NotNull String psuToken) {
        this.psuToken = psuToken;
    }

    public @NotNull(message = INVALID_CLAIM) String getClaims() {
        return claims;
    }

    public void setClaims(@NotNull(message = INVALID_CLAIM) String claims) {
        this.claims = claims;
    }

    public @NotNull String getAuthorizationScopes() {
        return authorizationScopes;
    }

    public void setAuthorizationScopes(@NotNull String authorizationScopes) {
        this.authorizationScopes = authorizationScopes;
    }

    public @NotNull LocalDateTime getCreatedtimes() {
        return createdtimes;
    }

    public void setCreatedtimes(@NotNull LocalDateTime createdtimes) {
        this.createdtimes = createdtimes;
    }

    public LocalDateTime getExpiredtimes() {
        return expiredtimes;
    }

    public void setExpiredtimes(LocalDateTime expiredtimes) {
        this.expiredtimes = expiredtimes;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getAcceptedClaims() {
        return acceptedClaims;
    }

    public void setAcceptedClaims(String acceptedClaims) {
        this.acceptedClaims = acceptedClaims;
    }

    public String getPermittedScopes() {
        return permittedScopes;
    }

    public void setPermittedScopes(String permittedScopes) {
        this.permittedScopes = permittedScopes;
    }
}
