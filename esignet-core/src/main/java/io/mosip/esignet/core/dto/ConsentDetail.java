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
import java.util.UUID;

@Data
public class ConsentDetail {
    private UUID id;
    private String clientId;
    private String psuToken;
    private Claims claims;
    Map<String, Boolean> authorizationScopes;
    private LocalDateTime createdtimes;
    private LocalDateTime expiredtimes;
    private String signature;
    private String hash;
    private List<String> acceptedClaims;
    private List<String> permittedScopes;
}
