/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import lombok.Data;

import java.util.Map;
import java.io.Serializable;

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

        for (Map.Entry<String, ClaimDetail> entry : firstClaimDetail.entrySet()) {
            String key = entry.getKey();
            ClaimDetail value = entry.getValue();
            ClaimDetail otherValue = secondClaimDetail.get(key);
            if (otherValue == null) {
                if (value != null) {
                    return false;
                }
            } else {
                if(value == null) {
                    return false;
                } else {
                    if(!value.isEqualToIgnoringAccepted(otherValue)){
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
