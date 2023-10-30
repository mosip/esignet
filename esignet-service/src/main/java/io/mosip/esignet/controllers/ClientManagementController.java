/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


@RestController
public class ClientManagementController {

    @Autowired
    ClientManagementService clientManagementService;

    @Autowired
    AuditPlugin auditWrapper;
    
    @Value("${mosip.esignet.audit.claim-name:preferred_username}")
    private String claimName;

    /**
     * @deprecated
     * This method is no longer acceptable to create oidc client
     * <p> Use {@link ClientManagementController#createOAuthClient(RequestWrapper<ClientDetailCreateRequest>)} </p>
     *
     * @param requestWrapper
     * @return
     * @throws EsignetException
     */
    @Deprecated()
    @RequestMapping(value = "/client-mgmt/oidc-client", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseWrapper<ClientDetailResponse> createClient(
            @Valid @RequestBody RequestWrapper<ClientDetailCreateRequest> requestWrapper) throws Exception {
        ResponseWrapper response = new ResponseWrapper<ClientDetailResponse>();
        try {
            response.setResponse(clientManagementService.createOIDCClient(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
            		Action.OIDC_CLIENT_CREATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getClientId()), ex);
            throw ex;
        }
        response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return response;
    }

    /**
     * @deprecated
     * This method is no longer acceptable to update oidc client
     * <p> Use {@link ClientManagementController#updateOAuthClient(String, RequestWrapper<ClientDetailUpdateRequest>)} </p>
     *
     * @param requestWrapper
     * @return
     * @throws EsignetException
     */
    @Deprecated()
    @RequestMapping(value = "/client-mgmt/oidc-client/{client_id}", method = RequestMethod.PUT,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseWrapper<ClientDetailResponse> updateClient(@Valid @PathVariable("client_id") String clientId,
                                                              @Valid @RequestBody RequestWrapper<ClientDetailUpdateRequest> requestWrapper) throws Exception {
        ResponseWrapper response = new ResponseWrapper<ClientDetailResponse>();
        try {
            response.setResponse(clientManagementService.updateOIDCClient(clientId, requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
            		Action.OIDC_CLIENT_UPDATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(clientId), ex);
            throw ex;
        }
        response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return response;
    }

    @PostMapping(value = "/client-mgmt/oauth-client", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseWrapper<ClientDetailResponse> createOAuthClient(@Valid @RequestBody RequestWrapper<ClientDetailCreateRequestV2> requestWrapper) throws Exception {
        ResponseWrapper response = new ResponseWrapper<ClientDetailResponse>();
        try {
            response.setResponse(clientManagementService.createOAuthClient(requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                    Action.OAUTH_CLIENT_CREATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(requestWrapper.getRequest().getClientId()), ex);
            throw ex;
        }
        response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return response;
    }


    @PutMapping(value = "/client-mgmt/oauth-client/{client_id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseWrapper<ClientDetailResponse> updateOAuthClient(@Valid @PathVariable("client_id") String clientId,
                                                              @Valid @RequestBody RequestWrapper<ClientDetailUpdateRequestV2> requestWrapper) throws Exception {
        ResponseWrapper response = new ResponseWrapper<ClientDetailResponse>();
        try {
            response.setResponse(clientManagementService.updateOAuthClient(clientId, requestWrapper.getRequest()));
        } catch (EsignetException ex) {
            auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                    Action.OAUTH_CLIENT_UPDATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(clientId), ex);
            throw ex;
        }
        response.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return response;
    }

}
