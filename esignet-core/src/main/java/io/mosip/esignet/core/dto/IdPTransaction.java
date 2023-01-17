/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import lombok.Data;

import java.util.List;
import java.io.Serializable;
import java.util.Set;

@Data
public class IdPTransaction implements Serializable {

    String clientId;
    String relyingPartyId;
    String redirectUri;
    Claims requestedClaims;
    List<String> requestedAuthorizeScopes;
    String[] claimsLocales;
    String authTransactionId;

    Set<List<String>> providedAuthFactors;
    String kycToken;
    String partnerSpecificUserToken;
    long authTimeInSeconds;
    String codeHash;

    List<String> acceptedClaims;
    List<String> permittedScopes;
    String encryptedKyc;
    String aHash;

    String linkedCodeHash;
    String linkedTransactionId;

    String nonce;
    String state;

    String individualId;
}
