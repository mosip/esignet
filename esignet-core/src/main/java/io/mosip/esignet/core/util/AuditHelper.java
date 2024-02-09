/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;

import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.core.dto.OIDCTransaction;

public class AuditHelper {

    public static AuditDTO buildAuditDto(String clientId) {
        AuditDTO auditDTO = new AuditDTO();
        auditDTO.setClientId(clientId);
        auditDTO.setTransactionId(clientId);
        auditDTO.setIdType("ClientId");
        return auditDTO;
    }

    public static AuditDTO buildAuditDto(String transactionId, OIDCTransaction transaction) {
        return buildAuditDto(transactionId, "transaction", transaction);
    }

    public static AuditDTO buildAuditDto(String transactionId, String idType, OIDCTransaction transaction) {
        AuditDTO auditDTO = new AuditDTO();
        auditDTO.setTransactionId(transactionId);
        auditDTO.setIdType(idType);
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
    
    public static String getClaimValue(SecurityContext context, String claimName) {
    	if (context.getAuthentication() == null) {
    		return null;
    	}
    	if (context.getAuthentication().getPrincipal() == null) {
    		return null;
    	}
    	if (context.getAuthentication().getPrincipal() instanceof Jwt) {
    		Jwt jwt = (Jwt) context.getAuthentication().getPrincipal();
    		return jwt.getClaim(claimName);
    	}
    	return null;
    }
}
