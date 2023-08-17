/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import lombok.Data;

import java.util.List;
import java.io.Serializable;
import java.util.Map;

@Data
public class ClientDetail implements Serializable {

    private String id;
    private Map<String, String> name;
    private String rpId;
    private String logoUri;
    private List<String> redirectUris;
    private String publicKey;
    private List<String> claims;
    private List<String> acrValues;
    private String status;
    private List<String> grantTypes;
    private List<String> clientAuthMethods;
}
