/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.ClientDetailRequest;
import io.mosip.idp.core.dto.ClientDetailResponse;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.dto.ResponseWrapper;
import io.mosip.idp.core.spi.ClientManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


/**
 * TODO - Add scope based pre-authorize annotations after integrating with auth-adapter
 */
@RestController
@RequestMapping("/client-mgmt")
public class ClientManagementController {

    private static final Logger logger = LoggerFactory.getLogger(ClientManagementController.class);

    @Autowired
    ClientManagementService clientRegistration;

    @PostMapping("/oidc-client")
    public ResponseWrapper<ClientDetailResponse> createClient(
            @Valid @RequestBody RequestWrapper<ClientDetailRequest> requestWrapper) {
        //TODO
        return null;
    }

    @PutMapping("/oidc-client/{client_id}")
    public ResponseWrapper<ClientDetailResponse> updateClient(@Valid @PathVariable("client_id")  String clientId,
                                                              @Valid @RequestBody RequestWrapper<ClientDetailRequest> requestWrapper)
    {
        //TODO
        return null;
    }
}
