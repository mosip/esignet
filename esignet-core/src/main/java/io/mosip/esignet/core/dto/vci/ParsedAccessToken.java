/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto.vci;

import lombok.Data;

import java.util.Map;

@Data
public class ParsedAccessToken {

    private Map<String, Object> claims;
    private String accessTokenHash;
    private boolean isActive;
}
