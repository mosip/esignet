/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.AuditWrapper;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.Action;
import io.mosip.idp.core.util.ActionStatus;
import io.mosip.idp.core.util.IdentityProviderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


/**
 * TODO - Add scope based pre-authorize annotations after integrating with auth-adapter
 */
@RestController
public class ClientManagementController {

    @Autowired
    ClientManagementService clientManagementService;

    @Autowired
    AuditWrapper auditWrapper;

    @RequestMapping(value = "/client-mgmt/oidc-client", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseWrapper<ClientDetailResponse> createClient(
            @Valid @RequestBody RequestWrapper<ClientDetailCreateRequest> requestWrapper) throws Exception {
        ResponseWrapper response = new ResponseWrapper<ClientDetailResponse>();
        try {
            response.setResponse(clientManagementService.createOIDCClient(requestWrapper.getRequest()));
        } catch (IdPException ex) {
            auditWrapper.logAudit(Action.OIDC_CLIENT_CREATE, ActionStatus.ERROR, new AuditDTO(requestWrapper.getRequest().getClientId()), ex);
            throw ex;
        }
        response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return response;
    }

    @RequestMapping(value = "/client-mgmt/oidc-client/{client_id}", method = RequestMethod.PUT,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseWrapper<ClientDetailResponse> updateClient(@Valid @PathVariable("client_id") String clientId,
                                                              @Valid @RequestBody RequestWrapper<ClientDetailUpdateRequest> requestWrapper) throws Exception {
        ResponseWrapper response = new ResponseWrapper<ClientDetailResponse>();
        try {
            response.setResponse(clientManagementService.updateOIDCClient(clientId, requestWrapper.getRequest()));
        } catch (IdPException ex) {
            auditWrapper.logAudit(Action.OIDC_CLIENT_UPDATE, ActionStatus.ERROR, new AuditDTO(clientId), ex);
            throw ex;
        }
        response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return response;
    }
}
