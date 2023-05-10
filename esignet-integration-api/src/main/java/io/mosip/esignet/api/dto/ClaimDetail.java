/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaimDetail implements Serializable {

    private String value;
    private String[] values;
    private boolean essential;
    private boolean accepted;

    public ClaimDetail(String value, String[] values, boolean essential) {
        this.value = value;
        this.values = values;
        this.essential = essential;
    }

    public boolean isEqualToIgnoringAccepted(ClaimDetail other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if (!Objects.equals(value, other.value)) {
            return false;
        }
        if (!Arrays.equals(values, other.values)) {
            return false;
        }
        return essential == other.essential;
    }
}
