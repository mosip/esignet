/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mapper;

import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.entity.ConsentHistory;
import org.modelmapper.PropertyMap;

public class CustomConsentHistoryMapping extends PropertyMap<UserConsent, ConsentHistory> {
    @Override
    protected void configure() {
         // Skip the 'id' field when mapping
        skip().setId(null);
    }
}


