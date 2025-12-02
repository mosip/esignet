/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import lombok.Data;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.io.Serializable;

/**
 * DTO used to store resolved claims based on the requested claims and registered claims in cache
 */
@Data
public class Claims implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    //We cannot use DTO, "data minimization" is one of the important requirement of https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html
    //Only the requested verification metadata should be given to RP.
    //If we use DTO, there is no way to differentiate between what is requested and which metadata is set to null during deserialization.
    //Hence, using JsonNode to store the resolved claim detail.
    private Map<String, List<Map<String, Object>>> userinfo;
    private Map<String, Map<String, Object>> id_token;
}
