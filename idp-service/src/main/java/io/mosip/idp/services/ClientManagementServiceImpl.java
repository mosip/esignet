/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.ClientDetailRequest;
import io.mosip.idp.core.dto.ClientDetailResponse;
import io.mosip.idp.core.spi.ClientManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClientManagementServiceImpl implements ClientManagementService {

    private static final Logger logger = LoggerFactory.getLogger(ClientManagementServiceImpl.class);

    @Override
    public ClientDetailResponse createOIDCClient(ClientDetailRequest clientDetailRequest) {
        return null;
    }

    @Override
    public ClientDetailResponse updateOIDCClient(ClientDetailRequest clientDetailRequest) {
        return null;
    }
}
