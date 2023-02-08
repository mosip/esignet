/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class KycExchangeDto {

    private String transactionId;
    private List<String> acceptedClaims;
    private String kycToken;
    private String[] claimsLocales;
    private String individualId;
    private String userInfoResponseType;
}
