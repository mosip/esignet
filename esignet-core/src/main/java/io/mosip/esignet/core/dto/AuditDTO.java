/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AuditDTO {

    String transactionId;
    String clientId;
    String relyingPartyId;
    String redirectUri;
    Claims requestedClaims;
    List<String> requestedAuthorizeScopes;
    String[] claimsLocales;
    String authTransactionId;
    long authTimeInSeconds;
    String codeHash;
    List<String> acceptedClaims;
    List<String> permittedScopes;
    String accessTokenHash;
    String linkedCodeHash;
    String linkedTransactionId;
    String nonce;
    String state;


    public AuditDTO(String clientId) {
        this.clientId = clientId;
    }

    public AuditDTO(String transactionId, IdPTransaction idPTransaction) {
        this.transactionId = transactionId;
        if(idPTransaction != null) {
            this.relyingPartyId = idPTransaction.getRelyingPartyId();
            this.clientId = idPTransaction.getClientId();
            this.requestedClaims = idPTransaction.getRequestedClaims();
            this.requestedAuthorizeScopes = idPTransaction.getRequestedAuthorizeScopes();
            this.redirectUri = idPTransaction.getRedirectUri();
            this.claimsLocales = idPTransaction.getClaimsLocales();
            this.authTransactionId = idPTransaction.getAuthTransactionId();
            this.authTimeInSeconds = idPTransaction.getAuthTimeInSeconds();
            this.codeHash = idPTransaction.getCodeHash();
            this.acceptedClaims = idPTransaction.getAcceptedClaims();
            this.permittedScopes = idPTransaction.getPermittedScopes();
            this.accessTokenHash = idPTransaction.getAHash();
            this.linkedCodeHash = idPTransaction.getLinkedCodeHash();
            this.linkedTransactionId = idPTransaction.getLinkedTransactionId();
            this.nonce = idPTransaction.getNonce();
            this.state = idPTransaction.getState();
        }
    }
}
