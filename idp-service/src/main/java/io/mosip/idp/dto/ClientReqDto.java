/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClientReqDto {

    //TODO Need to add DTO validations and java docs
    private String clientId;
    private String clientName;
    private String certificate;
    private String status;
    private String relayingPartyId;
    private String claims;
    private String authMethods;
    private String logoUri;
    private List<String> redirectUris;
}
