/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import io.mosip.esignet.api.dto.claim.Claims;
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

    String idType;
}
