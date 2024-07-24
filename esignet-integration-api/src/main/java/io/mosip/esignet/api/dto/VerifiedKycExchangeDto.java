/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import io.mosip.esignet.api.dto.claim.VerificationFilter;
import lombok.Data;
import java.util.Map;

@Data
public class VerifiedKycExchangeDto extends KycExchangeDto  {

    private Map<String, VerificationFilter> acceptedVerifiedClaims;
}
