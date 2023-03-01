/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ClientDetailCreateRequest;
import io.mosip.esignet.core.dto.ClientDetailResponse;
import io.mosip.esignet.core.dto.ClientDetailUpdateRequest;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.InvalidClientException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.util.*;
import io.mosip.esignet.entity.ClientDetail;
import io.mosip.esignet.repository.ClientDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.json.simple.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.mosip.esignet.core.constants.Constants.CLIENT_ACTIVE_STATUS;

@Slf4j
@Service
public class ClientManagementServiceImpl implements ClientManagementService {

    @Autowired
    ClientDetailRepository clientDetailRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuditPlugin auditWrapper;

    private List<String> NULL = Collections.singletonList(null);

    @CacheEvict(value = Constants.CLIENT_DETAIL_CACHE, key = "#clientDetailCreateRequest.getClientId()")
    @Override
    public ClientDetailResponse createOIDCClient(ClientDetailCreateRequest clientDetailCreateRequest) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findById(clientDetailCreateRequest.getClientId());
        if (result.isPresent()) {
            throw new IdPException(ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId(clientDetailCreateRequest.getClientId());
        clientDetail.setPublicKey(IdentityProviderUtil.getJWKString(clientDetailCreateRequest.getPublicKey()));
        clientDetail.setName(clientDetailCreateRequest.getClientName());
        clientDetail.setRpId(clientDetailCreateRequest.getRelyingPartyId());
        clientDetail.setLogoUri(clientDetailCreateRequest.getLogoUri());

        clientDetailCreateRequest.getRedirectUris().removeAll(NULL);
        clientDetail.setRedirectUris(JSONArray.toJSONString(clientDetailCreateRequest.getRedirectUris()));

        clientDetailCreateRequest.getUserClaims().removeAll(NULL);
        clientDetail.setClaims(JSONArray.toJSONString(clientDetailCreateRequest.getUserClaims()));

        clientDetailCreateRequest.getAuthContextRefs().removeAll(NULL);
        clientDetail.setAcrValues(JSONArray.toJSONString(clientDetailCreateRequest.getAuthContextRefs()));

        clientDetailCreateRequest.getGrantTypes().removeAll(NULL);
        clientDetail.setGrantTypes(JSONArray.toJSONString(clientDetailCreateRequest.getGrantTypes()));

        clientDetailCreateRequest.getClientAuthMethods().removeAll(NULL);
        clientDetail.setClientAuthMethods(JSONArray.toJSONString(clientDetailCreateRequest.getClientAuthMethods()));

        clientDetail.setStatus(CLIENT_ACTIVE_STATUS);
        clientDetail.setCreatedtimes(LocalDateTime.now(ZoneId.of("UTC")));

        try {
            clientDetail = clientDetailRepository.save(clientDetail);
        } catch (ConstraintViolationException cve) {
            log.error("Failed to create client details", cve);
            throw new IdPException(ErrorConstants.DUPLICATE_PUBLIC_KEY);
        }

        auditWrapper.logAudit(Action.OIDC_CLIENT_CREATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(clientDetailCreateRequest.getClientId()), null);

        var response = new ClientDetailResponse();
        response.setClientId(clientDetail.getId());
        response.setStatus(clientDetail.getStatus());
        return response;
    }

    @CacheEvict(value = Constants.CLIENT_DETAIL_CACHE, key = "#clientId")
    @Override
    public ClientDetailResponse updateOIDCClient(String clientId, ClientDetailUpdateRequest clientDetailUpdateRequest) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findById(clientId);
        if (!result.isPresent()) {
            throw new IdPException(ErrorConstants.INVALID_CLIENT_ID);
        }

        ClientDetail clientDetail = result.get();
        clientDetail.setName(clientDetailUpdateRequest.getClientName());
        clientDetail.setLogoUri(clientDetailUpdateRequest.getLogoUri());

        clientDetailUpdateRequest.getRedirectUris().removeAll(NULL);
        clientDetail.setRedirectUris(JSONArray.toJSONString(clientDetailUpdateRequest.getRedirectUris()));

        clientDetailUpdateRequest.getUserClaims().removeAll(NULL);
        clientDetail.setClaims(JSONArray.toJSONString(clientDetailUpdateRequest.getUserClaims()));

        clientDetailUpdateRequest.getAuthContextRefs().removeAll(NULL);
        clientDetail.setAcrValues(JSONArray.toJSONString(clientDetailUpdateRequest.getAuthContextRefs()));

        clientDetailUpdateRequest.getGrantTypes().removeAll(NULL);
        clientDetail.setGrantTypes(JSONArray.toJSONString(clientDetailUpdateRequest.getGrantTypes()));

        clientDetailUpdateRequest.getClientAuthMethods().removeAll(NULL);
        clientDetail.setClientAuthMethods(JSONArray.toJSONString(clientDetailUpdateRequest.getClientAuthMethods()));
        clientDetail.setStatus(clientDetailUpdateRequest.getStatus());
        clientDetail.setUpdatedtimes(LocalDateTime.now(ZoneId.of("UTC")));
        clientDetail = clientDetailRepository.save(clientDetail);

        auditWrapper.logAudit(Action.OIDC_CLIENT_UPDATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(clientId), null);

        var response = new ClientDetailResponse();
        response.setClientId(clientDetail.getId());
        response.setStatus(clientDetail.getStatus());
        return response;
    }

    @Cacheable(value = Constants.CLIENT_DETAIL_CACHE, key = "#clientId")
    @Override
    public io.mosip.esignet.core.dto.ClientDetail getClientDetails(String clientId) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(clientId, CLIENT_ACTIVE_STATUS);
        if(!result.isPresent())
            throw new InvalidClientException();

        io.mosip.esignet.core.dto.ClientDetail dto = new io.mosip.esignet.core.dto.ClientDetail();
        dto.setId(clientId);
        dto.setName(result.get().getName());
        dto.setRpId(result.get().getRpId());
        dto.setLogoUri(result.get().getLogoUri());
        dto.setStatus(result.get().getStatus());
        dto.setPublicKey(result.get().getPublicKey());
        TypeReference<List<String>> typeReference = new TypeReference<List<String>>() {};
        try {
            if(result.get().getClaims() != null)
                dto.setClaims(objectMapper.readValue(result.get().getClaims(), typeReference));
            dto.setAcrValues(objectMapper.readValue(result.get().getAcrValues(), typeReference));
            dto.setRedirectUris(objectMapper.readValue(result.get().getRedirectUris(), typeReference));
            dto.setGrantTypes(objectMapper.readValue(result.get().getGrantTypes(), typeReference));
            dto.setClientAuthMethods(objectMapper.readValue(result.get().getClientAuthMethods(), typeReference));
        } catch (Exception e) {
            log.error("Failed to parse json array", e);
            throw new InvalidClientException();
        }
        return dto;
    }
}
