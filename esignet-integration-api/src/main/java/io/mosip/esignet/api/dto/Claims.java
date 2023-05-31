/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.io.Serializable;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class Claims implements Serializable {

    private Map<String, ClaimDetail> userinfo;
    private Map<String, ClaimDetail> id_token;
    public boolean isEqualToIgnoringAccepted(final Claims claims) {
        if (claims == null) {
            return false;
        }
        if (this == claims) {
            return true;
        }
        return mapsEqualIgnoringAccepted(this.userinfo, claims.userinfo)
                && mapsEqualIgnoringAccepted(this.id_token, claims.id_token);
    }

    private boolean mapsEqualIgnoringAccepted(Map<String, ClaimDetail> firstClaimDetail, Map<String, ClaimDetail> secondClaimDetail) {
        if (firstClaimDetail == secondClaimDetail) {
            return true;
        }
        if (firstClaimDetail == null || secondClaimDetail == null) {
            return false;
        }
        if (firstClaimDetail.size() != secondClaimDetail.size()) {
            return false;
        }
        List<String> essentialClaimsFirstClaimDetail = firstClaimDetail.entrySet().stream()
                .filter(e -> Optional.ofNullable(e.getValue()).map(ClaimDetail::isEssential).orElse(false))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<String> essentialClaimsSecondClaimDetail = secondClaimDetail.entrySet().stream()
                .filter(e -> Optional.ofNullable(e.getValue()).map(ClaimDetail::isEssential).orElse(false))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return essentialClaimsFirstClaimDetail.equals(essentialClaimsSecondClaimDetail);
    }
}
