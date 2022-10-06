/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import lombok.Data;

import java.util.List;

@Data
public class KycExchangeRequest {

    private String relyingPartyId;
    private String clientId;
    private List<String> acceptedClaims;
    private String kycToken;
    private String[] claimsLocales;
}
