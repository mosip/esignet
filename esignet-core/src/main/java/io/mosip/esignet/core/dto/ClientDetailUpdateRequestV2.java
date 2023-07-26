/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ClientDetailUpdateRequestV2 extends ClientDetailUpdateRequest {

    @NotEmpty(message = ErrorConstants.INVALID_CLIENT_NAME)
    private Map<@Size(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_KEY, min = 3, max = 3) String,
            @NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_VALUE) String> clientNameLangMap;

    public ClientDetailUpdateRequestV2(String logUri, List<String> redirectUris, List<String> userClaims, List<String> authContextRefs, String status, List<String> grantTypes, String clientName, List<String> clientAuthMethods, Map<String,String> clientNameLangMap){
        super(logUri,redirectUris,userClaims,authContextRefs,status,grantTypes,clientName,clientAuthMethods);
        this.clientNameLangMap=clientNameLangMap;

    }
}
