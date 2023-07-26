/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OAuthDetailResponse {

    private String transactionId;
    private String logoUrl;
    private List<List<AuthenticationFactor>> authFactors;
    private List<String> authorizeScopes;
    private List<String> essentialClaims;
    private List<String> voluntaryClaims;
    private Map<String, Object> configs;
    private String redirectUri;

}
