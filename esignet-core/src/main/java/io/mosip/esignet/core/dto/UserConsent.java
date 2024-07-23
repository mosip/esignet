/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.api.dto.claim.Claims;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class UserConsent {
    String psuToken;
    String clientId;
    Claims Claims;
    Map<String, Boolean> authorizationScopes;
    LocalDateTime expirydtimes;
    String signature;
    String hash;
    List<String> acceptedClaims;
    List<String> permittedScopes;
}
