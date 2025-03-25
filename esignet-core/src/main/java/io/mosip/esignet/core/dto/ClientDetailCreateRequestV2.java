/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.ClientNameLang;
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

    private Map<@ClientNameLang String,
            @NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_VALUE) @Size(max = 50,
                    message = ErrorConstants.INVALID_CLIENT_NAME_LENGTH) String> clientNameLangMap;
            
    public ClientDetailCreateRequestV2(String clientId, String clientName, Map<String, Object> publicKey, String relyingPartyId,
                                       List<String> userClaims, List<String> authContextRefs, String logoUri,
                                       List<String> redirectUris, List<String> grantTypes, List<String> clientAuthMethods,
                                       Map<String, String> clientNameLangMap) {
        super(clientId, clientName, publicKey, relyingPartyId, userClaims, authContextRefs, logoUri, redirectUris,
                grantTypes, clientAuthMethods);
        this.clientNameLangMap = clientNameLangMap;
    }

}
