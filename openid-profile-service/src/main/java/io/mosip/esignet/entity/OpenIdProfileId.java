/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Setter
@Getter
public class OpenIdProfileId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String profileName;
    private String feature;

    public OpenIdProfileId() {}

    public OpenIdProfileId(String profileName, String feature) {
        this.profileName = profileName;
        this.feature = feature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpenIdProfileId)) return false;
        OpenIdProfileId that = (OpenIdProfileId) o;
        return Objects.equals(profileName, that.profileName) &&
                Objects.equals(feature, that.feature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileName, feature);
    }
}
