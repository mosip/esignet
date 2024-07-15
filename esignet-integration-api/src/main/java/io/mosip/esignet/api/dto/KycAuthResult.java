/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import io.mosip.esignet.api.dto.claim.VerificationDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class KycAuthResult {

    private String kycToken;
    private String partnerSpecificUserToken;
    private Map<String, List<VerificationDetail>> claimsMetadata;

    public KycAuthResult(String kycToken, String partnerSpecificUserToken) {
        this.kycToken = kycToken;
        this.partnerSpecificUserToken = partnerSpecificUserToken;
    }
}
