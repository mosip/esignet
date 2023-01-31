package io.mosip.esignet.core.util;

import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.core.dto.IdPTransaction;

public class AuditHelper {

    public static AuditDTO buildAuditDto(String clientId) {
        AuditDTO auditDTO = new AuditDTO();
        auditDTO.setClientId(clientId);
        return auditDTO;
    }

    public static AuditDTO buildAuditDto(String transactionId, IdPTransaction transaction) {
        AuditDTO auditDTO = new AuditDTO();
        auditDTO.setTransactionId(transactionId);
        if(transaction != null) {
            auditDTO.setRelyingPartyId(transaction.getRelyingPartyId());
            auditDTO.setClientId(transaction.getClientId());
            auditDTO.setRequestedClaims(transaction.getRequestedClaims());
            auditDTO.setRequestedAuthorizeScopes(transaction.getRequestedAuthorizeScopes());
            auditDTO.setRedirectUri(transaction.getRedirectUri());
            auditDTO.setClaimsLocales(transaction.getClaimsLocales());
            auditDTO.setAuthTransactionId(transaction.getAuthTransactionId());
            auditDTO.setAuthTimeInSeconds(transaction.getAuthTimeInSeconds());
            auditDTO.setCodeHash(transaction.getCodeHash());
            auditDTO.setAcceptedClaims(transaction.getAcceptedClaims());
            auditDTO.setPermittedScopes(transaction.getPermittedScopes());
            auditDTO.setAccessTokenHash(transaction.getAHash());
            auditDTO.setLinkedCodeHash(transaction.getLinkedCodeHash());
            auditDTO.setLinkedTransactionId(transaction.getLinkedTransactionId());
            auditDTO.setNonce(transaction.getNonce());
            auditDTO.setState(transaction.getState());
        }
        return auditDTO;
    }
}
