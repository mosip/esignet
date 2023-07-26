/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientDetailCreateRequestV2 extends ClientDetailCreateRequest {

    @NotEmpty(message = ErrorConstants.INVALID_CLIENT_NAME)
    private Map<@Size(message= ErrorConstants.INVALID_CLIENT_NAME_MAP_KEY, min=3, max=3) String,
            @NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_VALUE) String> clientNameLangMap;
            
    public ClientDetailCreateRequestV2(String clientId, String clientName, Map<String, Object> publicKey, String relyingPartyId,
                                       List<String> userClaims, List<String> authContextRefs, String logoUri,
                                       List<String> redirectUris, List<String> grantTypes, List<String> clientAuthMethods,
                                       Map<String, String> clientNameLangMap) {
        super(clientId, clientName, publicKey, relyingPartyId, userClaims, authContextRefs, logoUri, redirectUris,
                grantTypes, clientAuthMethods);
        this.clientNameLangMap = clientNameLangMap;
    }

}
