/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.exception.EsignetException;
import java.util.List;

public interface OpenIdProfileService {

    /**
     * Get the features associated with the profile
     * @param profileName name of the profile - fapi2.0. nisdsp, gov, none etc
     * @return list of features associated with the profile
     */
    List<String> getFeaturesByProfileName(String profileName) throws EsignetException;
}
