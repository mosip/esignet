/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.idp.core.dto.ClientDetailCreateRequest;
import io.mosip.idp.core.dto.ClientDetailResponse;
import io.mosip.idp.core.dto.ClientDetailUpdateRequest;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.entity.ClientDetail;
import io.mosip.idp.repository.ClientDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.json.simple.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.mosip.idp.core.util.Constants.CLIENT_ACTIVE_STATUS;

@Slf4j
@Service
public class ClientManagementServiceImpl implements ClientManagementService {

    @Autowired
    ClientDetailRepository clientDetailRepository;

    @Autowired
    ObjectMapper objectMapper;

    @CacheEvict(value = Constants.CLIENT_DETAIL_CACHE, key = "#clientDetailCreateRequest.getClientId()")
    @Override
    public ClientDetailResponse createOIDCClient(ClientDetailCreateRequest clientDetailCreateRequest) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findById(clientDetailCreateRequest.getClientId());
        if (result.isPresent()) {
            throw new IdPException(ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId(clientDetailCreateRequest.getClientId());
        clientDetail.setPublicKey(getJWKString(clientDetailCreateRequest.getPublicKey()));
        clientDetail.setName(clientDetailCreateRequest.getClientName());
        clientDetail.setRpId(clientDetailCreateRequest.getRelyingPartyId());
        clientDetail.setLogoUri(clientDetailCreateRequest.getLogoUri());
        clientDetail.setRedirectUris(JSONArray.toJSONString(clientDetailCreateRequest.getRedirectUris()));
        clientDetail.setClaims(JSONArray.toJSONString(clientDetailCreateRequest.getUserClaims()));
        clientDetail.setAcrValues(JSONArray.toJSONString(clientDetailCreateRequest.getAuthContextRefs()));
        clientDetail.setStatus(CLIENT_ACTIVE_STATUS);
        clientDetail.setGrantTypes(JSONArray.toJSONString(clientDetailCreateRequest.getGrantTypes()));
        clientDetail.setClientAuthMethods(JSONArray.toJSONString(clientDetailCreateRequest.getClientAuthMethods()));
        clientDetail.setCreatedtimes(LocalDateTime.now(ZoneId.of("UTC")));
        clientDetail = clientDetailRepository.save(clientDetail);

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
        clientDetail.setRedirectUris(JSONArray.toJSONString(clientDetailUpdateRequest.getRedirectUris()));
        clientDetail.setClaims(JSONArray.toJSONString(clientDetailUpdateRequest.getUserClaims()));
        clientDetail.setAcrValues(JSONArray.toJSONString(clientDetailUpdateRequest.getAuthContextRefs()));
        clientDetail.setGrantTypes(JSONArray.toJSONString(clientDetailUpdateRequest.getGrantTypes()));
        clientDetail.setClientAuthMethods(JSONArray.toJSONString(clientDetailUpdateRequest.getClientAuthMethods()));
        clientDetail.setStatus(clientDetailUpdateRequest.getStatus());
        clientDetail.setUpdatedtimes(LocalDateTime.now(ZoneId.of("UTC")));
        clientDetail = clientDetailRepository.save(clientDetail);

        var response = new ClientDetailResponse();
        response.setClientId(clientDetail.getId());
        response.setStatus(clientDetail.getStatus());
        return response;
    }

    @Cacheable(value = Constants.CLIENT_DETAIL_CACHE, key = "#clientId")
    @Override
    public io.mosip.idp.core.dto.ClientDetail getClientDetails(String clientId) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(clientId, CLIENT_ACTIVE_STATUS);
        if(!result.isPresent())
            throw new InvalidClientException();

        io.mosip.idp.core.dto.ClientDetail dto = new io.mosip.idp.core.dto.ClientDetail();
        dto.setId(clientId);
        dto.setName(result.get().getName());
        dto.setRpId(result.get().getRpId());
        dto.setLogoUri(result.get().getLogoUri());
        dto.setStatus(result.get().getStatus());
        dto.setPublicKey(result.get().getPublicKey());
        TypeReference<List<String>> typeReference = new TypeReference<List<String>>() {};
        try {
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

    private String getJWKString(Map<String, Object> jwk) throws IdPException {
        try {
            RsaJsonWebKey jsonWebKey = new RsaJsonWebKey(jwk);
            return jsonWebKey.toJson();
        } catch (JoseException e) {
            log.error(ErrorConstants.INVALID_PUBLIC_KEY, e);
            throw new IdPException(ErrorConstants.INVALID_PUBLIC_KEY);
        }
    }
}
