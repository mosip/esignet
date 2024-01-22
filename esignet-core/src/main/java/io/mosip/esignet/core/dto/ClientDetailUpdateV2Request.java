/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.validator.AuthContextRef;
import io.mosip.esignet.core.validator.OIDCClaim;
import io.mosip.esignet.core.validator.OIDCClientAuth;
import io.mosip.esignet.core.validator.OIDCGrantType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ClientDetailUpdateV2Request extends ClientDetailUpdateRequest {

    @NotEmpty(message = ErrorConstants.INVALID_CLIENT_NAME)
    private Map<@Size(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_KEY, min = 3, max = 3) String,
            @NotBlank(message = ErrorConstants.INVALID_CLIENT_NAME_MAP_VALUE) String> clientNameLangMap;

    public ClientDetailUpdateV2Request(String logUri,List<String> redirectUris,List<String> userClaims,List<String> authContextRefs,String status,List<String> grantTypes,String clientName,List<String> clientAuthMethods,Map<String,String> clientNameLangMap){
        super(logUri,redirectUris,userClaims,authContextRefs,status,grantTypes,clientName,clientAuthMethods);
        this.clientNameLangMap=clientNameLangMap;

    }
}
