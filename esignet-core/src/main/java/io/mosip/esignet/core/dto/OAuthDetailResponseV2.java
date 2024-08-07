/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OAuthDetailResponseV2 extends OAuthDetailResponse {

    private Map<String, String> clientName;
    private List<String> credentialScopes;
}
