/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaimsV2 implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //We cannot use DTO, "data minimization" is one of the important requirement of https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html
    //Only the requested verification metadata should be given to RP.
    //If we use DTO, there is no way to differentiate between what is requested and which metadata is set to null during deserialization.
    //Hence, using JsonNode to store the resolved claim detail.
    private Map<String, JsonNode> userinfo;
    private Map<String, ClaimDetail> id_token;
}
