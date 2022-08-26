/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdPTransaction {

    String clientId;
    String redirectUri;
    Claims requestedClaims;
    String scopes;

    String kycToken;
    String userToken;
    long authTimeInSeconds;
    String code;

    List<String> acceptedClaims;
    String encryptedKyc;
    String idHash;
    String aHash;

    String error;

    String nonce;
}
