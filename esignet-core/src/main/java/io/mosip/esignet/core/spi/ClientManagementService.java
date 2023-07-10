/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import org.springframework.cache.annotation.CacheEvict;

public interface ClientManagementService {

    /**
     * API to register relying party client
     * @param clientDetailCreateRequest
     * @return
     * @throws EsignetException
     */
    ClientDetailResponse createOIDCClient(ClientDetailCreateRequest clientDetailCreateRequest) throws EsignetException;

    /**
     * API to update registered relying party client
     * @param clientId
     * @param clientDetailCreateRequest
     * @return
     * @throws EsignetException
     */
    ClientDetailResponse updateOIDCClient(String clientId, ClientDetailUpdateRequest clientDetailCreateRequest) throws EsignetException;

    /**
     * Api to get the active client detail with the provided client id.
     * @param clientId
     * @return
     */
    ClientDetail getClientDetails(String clientId) throws EsignetException;

    /**
     * API to register relying party client
     * @param clientDetailCreateV2Request
     * @return
     * @throws EsignetException
     */
    ClientDetailResponse createOIDCClientV2(ClientDetailCreateV2Request clientDetailCreateV2Request) throws EsignetException;

    /**
     * API to update registered relying party client
     * @param clientId
     * @param clientDetailUpdateV2Request
     * @return
     * @throws EsignetException
     */
    ClientDetailResponse updateOIDCClientV2(String clientId, ClientDetailUpdateV2Request clientDetailUpdateV2Request) throws EsignetException;

}
